package com.jessica.crm_vendas.dto;

import java.time.Instant;

import com.jessica.crm_vendas.model.InboundLead;

/** Lead recebido, como aparece na tela de Integrações. */
public record InboundLeadDTO(
    Long id,
    String provider,
    String formName,
    Long customerId,
    String customerName,
    Long dealId,
    String dealTitle,
    Instant receivedAt
) {
    public static InboundLeadDTO from(InboundLead lead) {
        return new InboundLeadDTO(
            lead.getId(),
            lead.getProvider(),
            lead.getFormName(),
            lead.getCustomer() == null ? null : lead.getCustomer().getId(),
            lead.getCustomer() == null ? null : lead.getCustomer().getName(),
            lead.getDeal() == null ? null : lead.getDeal().getId(),
            lead.getDeal() == null ? null : lead.getDeal().getTitle(),
            lead.getReceivedAt()
        );
    }
}
