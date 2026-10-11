package com.jessica.crm_vendas.dto;

import java.util.List;

/**
 * Resumo da importação de planilha.
 *
 * @param rows              linhas de dados lidas
 * @param imported          linhas que viraram negócio novo
 * @param newCustomers      quantos contatos novos foram criados (os outros já existiam)
 * @param duplicates        linhas já importadas antes (ignoradas)
 * @param skipped           linhas sem nome, telefone nem e-mail
 * @param issues            detalhes das linhas ignoradas (até 50)
 * @param recognizedColumns colunas entendidas (nome, telefone, e-mail…)
 * @param extraColumns      colunas guardadas nas observações
 */
public record CsvImportResultDTO(
    int rows,
    int imported,
    int newCustomers,
    int duplicates,
    int skipped,
    List<RowIssue> issues,
    List<String> recognizedColumns,
    List<String> extraColumns
) {
    public record RowIssue(int line, String message) {}
}
