package com.jessica.crm_vendas.integration;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.jessica.crm_vendas.exception.BusinessRuleException;
import com.jessica.crm_vendas.integration.netlify.NetlifySignatureVerifier;

/**
 * Lê planilhas de leads (CSV do Netlify, Excel, Google Sheets…).
 * Reconhece as colunas pelo nome, em português ou inglês, com ou sem acento;
 * aceita vírgula ou ponto e vírgula, UTF-8 ou o padrão do Excel no Windows.
 * Colunas não reconhecidas viram "extras" e vão para as observações do negócio.
 */
final class CsvLeadParser {

    static final int MAX_ROWS = 2000;

    enum Field { NAME, PHONE, EMAIL, COMPANY, RESULT, SCORE, ID, DATE }

    private static final Map<String, Field> SYNONYMS = new LinkedHashMap<>();
    static {
        for (String key : List.of("nome", "name", "nomecompleto", "fullname", "contato", "cliente")) SYNONYMS.put(key, Field.NAME);
        for (String key : List.of("telefone", "phone", "whatsapp", "celular", "fone", "tel", "telefonewhatsapp")) SYNONYMS.put(key, Field.PHONE);
        for (String key : List.of("email", "mail", "emailaddress")) SYNONYMS.put(key, Field.EMAIL);
        for (String key : List.of("empresa", "company", "organizacao", "negocio")) SYNONYMS.put(key, Field.COMPANY);
        for (String key : List.of("resultado", "result", "diagnostico")) SYNONYMS.put(key, Field.RESULT);
        for (String key : List.of("placar", "score", "pontuacao")) SYNONYMS.put(key, Field.SCORE);
        for (String key : List.of("id", "submissionid")) SYNONYMS.put(key, Field.ID);
        for (String key : List.of("createdat", "data", "date", "criadoem", "datadeenvio", "enviadoem")) SYNONYMS.put(key, Field.DATE);
    }

    /** Colunas técnicas do Netlify e de formulários que não interessam ao CRM. */
    private static final Set<String> IGNORED = Set.of("ip", "useragent", "referrer", "botfield", "formname", "formid", "siteurl");

    private static final List<DateTimeFormatter> LOCAL_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

    record Row(int line, Map<String, String> raw, LeadData lead, String externalId, Instant date) {}

    record Result(List<Row> rows, List<String> recognizedColumns, List<String> extraColumns) {}

    private CsvLeadParser() {}

    static Result parse(byte[] content, String fileLabel, ZoneId zone) {
        String text = decode(content);
        if (text.isBlank()) {
            throw new BusinessRuleException("O arquivo está vazio.");
        }
        char delimiter = detectDelimiter(text);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setAllowMissingColumnNames(true)
                .setAllowDuplicateHeaderNames(true)
                .build();

        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            List<String> headers = parser.getHeaderNames();
            Map<String, Field> mapping = new LinkedHashMap<>();
            List<String> extraColumns = new ArrayList<>();
            for (String header : headers) {
                String key = normalize(header);
                Field field = SYNONYMS.get(key);
                if (field != null && !mapping.containsValue(field)) {
                    mapping.put(header, field);
                } else if (!key.isEmpty() && !IGNORED.contains(key)) {
                    extraColumns.add(header);
                }
            }

            if (!mapping.containsValue(Field.NAME) && !mapping.containsValue(Field.PHONE) && !mapping.containsValue(Field.EMAIL)) {
                throw new BusinessRuleException("Não encontrei colunas de nome, telefone ou e-mail. Cabeçalhos do arquivo: "
                        + String.join(", ", headers));
            }

            List<Row> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_ROWS) {
                    throw new BusinessRuleException("O arquivo tem mais de " + MAX_ROWS + " linhas. Divida em arquivos menores.");
                }
                // Linha como aparece no Excel: a 1 é o cabeçalho, os dados começam na 2
                rows.add(toRow(record, rows.size() + 2, mapping, extraColumns, fileLabel, zone));
            }
            if (rows.isEmpty()) {
                throw new BusinessRuleException("O arquivo só tem o cabeçalho, sem nenhuma linha de lead.");
            }

            List<String> recognized = mapping.keySet().stream().toList();
            return new Result(rows, recognized, extraColumns);
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new BusinessRuleException("Não consegui ler o arquivo como CSV: " + e.getMessage());
        }
    }

    private static Row toRow(CSVRecord record, int line, Map<String, Field> mapping, List<String> extraColumns,
                             String fileLabel, ZoneId zone) {
        Map<Field, String> values = new LinkedHashMap<>();
        Map<String, String> raw = new LinkedHashMap<>();
        mapping.forEach((header, field) -> {
            String value = value(record, header);
            values.put(field, value);
            if (value != null) raw.put(header, value);
        });
        Map<String, String> extras = new LinkedHashMap<>();
        for (String header : extraColumns) {
            String value = value(record, header);
            if (value != null) {
                extras.put(header, value);
                raw.put(header, value);
            }
        }

        LeadData lead = new LeadData(
                values.get(Field.NAME), values.get(Field.PHONE), values.get(Field.EMAIL), values.get(Field.COMPANY),
                values.get(Field.RESULT), values.get(Field.SCORE),
                "Planilha " + fileLabel, "Lead importado", extras);

        // Sem coluna de ID, a própria linha vira a identidade: importar o mesmo arquivo de novo não duplica nada
        String externalId = values.get(Field.ID) != null
                ? truncate(values.get(Field.ID), 100)
                : NetlifySignatureVerifier.sha256Hex(String.join("|",
                        nullToEmpty(lead.name()).toLowerCase(Locale.ROOT),
                        nullToEmpty(PhoneNumbers.digits(lead.phone())),
                        nullToEmpty(lead.email()).toLowerCase(Locale.ROOT),
                        nullToEmpty(values.get(Field.DATE)),
                        nullToEmpty(lead.result())).getBytes(StandardCharsets.UTF_8));

        return new Row(line, raw, lead, externalId, parseDate(values.get(Field.DATE), zone));
    }

    private static String value(CSVRecord record, String header) {
        if (!record.isMapped(header) || !record.isSet(header)) return null;
        String value = record.get(header);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** UTF-8 (com ou sem BOM); se não for UTF-8 válido, assume o padrão do Excel no Windows. */
    static String decode(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            return new String(content, Charset.forName("windows-1252"));
        }
    }

    /** Excel em português separa por ";"; o resto do mundo, por ",". Decide pela primeira linha. */
    static char detectDelimiter(String text) {
        String firstLine = text.lines().findFirst().orElse("");
        long semicolons = firstLine.chars().filter(c -> c == ';').count();
        long commas = firstLine.chars().filter(c -> c == ',').count();
        return semicolons > commas ? ';' : ',';
    }

    /** "E-mail", "e_mail" e "EMAIL" viram "email"; "Criado em" vira "criadoem". */
    static String normalize(String header) {
        String withoutAccents = Normalizer.normalize(header == null ? "" : header, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static Instant parseDate(String value, ZoneId zone) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // tenta os formatos sem fuso
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // tenta os formatos sem fuso
        }
        for (DateTimeFormatter formatter : LOCAL_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter).atZone(zone).toInstant();
            } catch (DateTimeParseException ignored) {
                // próximo formato
            }
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy")).atStartOfDay(zone).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
