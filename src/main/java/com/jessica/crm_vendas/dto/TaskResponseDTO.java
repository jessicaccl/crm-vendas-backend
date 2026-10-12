package com.jessica.crm_vendas.dto;

import java.time.Instant;

import com.jessica.crm_vendas.model.Task;

public record TaskResponseDTO(
    Long id,
    String title,
    Instant dueAt,
    Instant doneAt,
    CustomerRef customer,
    DealRef deal,
    Instant createdAt
) {
    public record CustomerRef(Long id, String name, String company) {}

    public record DealRef(Long id, String title) {}

    public static TaskResponseDTO from(Task task) {
        return new TaskResponseDTO(
            task.getId(),
            task.getTitle(),
            task.getDueAt(),
            task.getDoneAt(),
            task.getCustomer() == null ? null
                    : new CustomerRef(task.getCustomer().getId(), task.getCustomer().getName(), task.getCustomer().getCompany()),
            task.getDeal() == null ? null : new DealRef(task.getDeal().getId(), task.getDeal().getTitle()),
            task.getCreatedAt()
        );
    }
}
