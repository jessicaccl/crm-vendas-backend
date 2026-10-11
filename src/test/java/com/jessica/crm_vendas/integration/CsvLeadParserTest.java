package com.jessica.crm_vendas.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.jessica.crm_vendas.exception.BusinessRuleException;

class CsvLeadParserTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private CsvLeadParser.Result parse(String csv) {
        return CsvLeadParser.parse(csv.getBytes(StandardCharsets.UTF_8), "leads.csv", SAO_PAULO);
    }

    @Test
    void readsNetlifyStyleExportAndKeepsUnknownColumnsAsExtras() {
        CsvLeadParser.Result result = parse("""
                nome,telefone,email,resultado,placar,ip,created_at,como conheceu
                Marina Costa,21987654321,,Página Profissional (One Page),"Presença: 12 · Social: 4 · Automação: 2",200.0.0.1,2026-08-31T14:00:00Z,Instagram
                """);

        assertThat(result.rows()).hasSize(1);
        CsvLeadParser.Row row = result.rows().get(0);
        assertThat(row.lead().name()).isEqualTo("Marina Costa");
        assertThat(row.lead().phone()).isEqualTo("21987654321");
        assertThat(row.lead().email()).isNull();
        assertThat(row.lead().result()).isEqualTo("Página Profissional (One Page)");
        assertThat(row.lead().score()).contains("Presença: 12");
        assertThat(row.lead().extras()).containsEntry("como conheceu", "Instagram").doesNotContainKey("ip");
        assertThat(row.date()).isEqualTo(Instant.parse("2026-08-31T14:00:00Z"));
        assertThat(row.line()).isEqualTo(2);
        assertThat(result.extraColumns()).containsExactly("como conheceu");
    }

    @Test
    void readsExcelInPortugueseWithSemicolonsAccentsAndWindowsEncoding() {
        String csv = "Nome Completo;WhatsApp;E-mail;Empresa;Data\r\n"
                + "José Araújo;(11) 98888-7777;JOSE@ACOUGUE.COM.BR;Açougue do Zé;31/08/2026 10:30\r\n";
        CsvLeadParser.Result result = CsvLeadParser.parse(csv.getBytes(Charset.forName("windows-1252")), "x.csv", SAO_PAULO);

        CsvLeadParser.Row row = result.rows().get(0);
        assertThat(row.lead().name()).isEqualTo("José Araújo");
        assertThat(row.lead().phone()).isEqualTo("(11) 98888-7777");
        assertThat(row.lead().email()).isEqualTo("JOSE@ACOUGUE.COM.BR");
        assertThat(row.lead().company()).isEqualTo("Açougue do Zé");
        assertThat(row.date()).isEqualTo(Instant.parse("2026-08-31T13:30:00Z"));
    }

    @Test
    void ignoresUtf8BomAndBlankLines() {
        CsvLeadParser.Result result = parse("﻿name,phone\n\nAna,21999990000\n\n");
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).lead().name()).isEqualTo("Ana");
    }

    @Test
    void sameRowAlwaysGetsTheSameIdentitySoReimportDoesNotDuplicate() {
        String csv = "nome,telefone\nAna,21999990000\n";
        assertThat(parse(csv).rows().get(0).externalId()).isEqualTo(parse(csv).rows().get(0).externalId());
        assertThat(parse("nome,telefone\nAna,(21) 99999-0000\n").rows().get(0).externalId())
                .as("mesma pessoa com o telefone formatado de outro jeito")
                .isEqualTo(parse(csv).rows().get(0).externalId());
    }

    @Test
    void rejectsFilesWithoutContactColumnsOrRows() {
        assertThatThrownBy(() -> parse("produto,valor\nSite,1000\n"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("produto, valor");
        assertThatThrownBy(() -> parse("nome,telefone\n"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("só tem o cabeçalho");
        assertThatThrownBy(() -> parse("   "))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void normalizesHeaders() {
        assertThat(CsvLeadParser.normalize("E-mail")).isEqualTo("email");
        assertThat(CsvLeadParser.normalize("Criado em")).isEqualTo("criadoem");
        assertThat(CsvLeadParser.normalize("Organização")).isEqualTo("organizacao");
    }
}
