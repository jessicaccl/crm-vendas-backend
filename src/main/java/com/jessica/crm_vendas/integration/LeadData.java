package com.jessica.crm_vendas.integration;

import java.util.Map;

/**
 * Dados de um lead vindo de fora (webhook ou planilha), já no vocabulário do CRM.
 *
 * @param origin        de onde veio, para as observações (ex.: "Netlify Forms (leads-diagnostico)")
 * @param fallbackTitle título do negócio quando não há resultado de diagnóstico
 * @param extras        outros campos recebidos, guardados nas observações para nada se perder
 */
public record LeadData(
    String name,
    String phone,
    String email,
    String company,
    String result,
    String score,
    String origin,
    String fallbackTitle,
    Map<String, String> extras
) {
    public boolean hasContact() {
        return name != null || phone != null || email != null;
    }
}
