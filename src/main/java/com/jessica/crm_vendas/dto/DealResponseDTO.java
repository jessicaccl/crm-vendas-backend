package com.jessica.crm_vendas.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.Deal;
import com.jessica.crm_vendas.model.DealSource;
import com.jessica.crm_vendas.model.DealStage;

public record DealResponseDTO(
    Long id,
    String title,
    BigDecimal value,
    DealStage stage,
    DealSource source,
    LocalDate expectedCloseDate,
    String lostReason,
    String notes,
    CustomerSummary customer,
    Instant stageChangedAt,
    Instant closedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public record CustomerSummary(Long id, String name, String company) {
        static CustomerSummary from(Customer customer) {
            return new CustomerSummary(customer.getId(), customer.getName(), customer.getCompany());
        }
    }

    public static DealResponseDTO from(Deal deal) {
        return new DealResponseDTO(
            deal.getId(),
            deal.getTitle(),
            deal.getValue(),
            deal.getStage(),
            deal.getSource(),
            deal.getExpectedCloseDate(),
            deal.getLostReason(),
            deal.getNotes(),
            CustomerSummary.from(deal.getCustomer()),
            deal.getStageChangedAt(),
            deal.getClosedAt(),
            deal.getCreatedAt(),
            deal.getUpdatedAt()
        );
    }
}
