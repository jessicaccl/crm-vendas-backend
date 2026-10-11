package com.jessica.crm_vendas.integration;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jessica.crm_vendas.dto.CsvImportResultDTO;
import com.jessica.crm_vendas.dto.LeadIntakeResultDTO;
import com.jessica.crm_vendas.exception.BusinessRuleException;
import com.jessica.crm_vendas.model.DealSource;
import com.jessica.crm_vendas.security.TenantContext;

import lombok.RequiredArgsConstructor;

/** Importa uma planilha de leads para a organização logada, com a mesma regra do webhook. */
@Service
@RequiredArgsConstructor
public class CsvImportService {

    static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ISSUES = 50;

    private final LeadIntakeService intake;
    private final TenantContext tenant;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public CsvImportResultDTO importLeads(MultipartFile file, DealSource source) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Escolha um arquivo CSV.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("O arquivo passa de 2 MB. Divida em arquivos menores.");
        }

        Long org = tenant.organizationId();
        String fileLabel = fileLabel(file.getOriginalFilename());
        CsvLeadParser.Result parsed = CsvLeadParser.parse(read(file), fileLabel, clock.getZone());

        int imported = 0;
        int newCustomers = 0;
        int duplicates = 0;
        int skipped = 0;
        List<CsvImportResultDTO.RowIssue> issues = new ArrayList<>();

        for (CsvLeadParser.Row row : parsed.rows()) {
            if (!row.lead().hasContact()) {
                skipped++;
                if (issues.size() < MAX_ISSUES) {
                    issues.add(new CsvImportResultDTO.RowIssue(row.line(), "Linha sem nome, telefone nem e-mail."));
                }
                continue;
            }
            LeadIntakeResultDTO result = intake.register(org, LeadIntakeService.CSV, row.externalId(), fileLabel,
                    row.lead(), toJson(row), row.date() != null ? row.date() : clock.instant(), source);
            if (result.duplicate()) {
                duplicates++;
            } else {
                imported++;
                if (result.newCustomer()) newCustomers++;
            }
        }

        return new CsvImportResultDTO(parsed.rows().size(), imported, newCustomers, duplicates, skipped, issues,
                parsed.recognizedColumns(), parsed.extraColumns());
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("Não consegui ler o arquivo enviado.");
        }
    }

    /** Só o nome do arquivo, sem caminho, para aparecer nas observações e na lista de leads. */
    private static String fileLabel(String originalName) {
        if (originalName == null || originalName.isBlank()) return "importada";
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        return name.length() > 90 ? name.substring(0, 90) : name;
    }

    private String toJson(CsvLeadParser.Row row) {
        try {
            return objectMapper.writeValueAsString(row.raw());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
