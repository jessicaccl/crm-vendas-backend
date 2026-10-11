package com.jessica.crm_vendas.dto;

/** Resposta ao Netlify: o que foi criado a partir do envio. */
public record LeadIntakeResultDTO(Long customerId, Long dealId, boolean newCustomer, boolean duplicate) {}
