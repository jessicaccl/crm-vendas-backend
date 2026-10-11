package com.jessica.crm_vendas.dto;

import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.CustomerStatus;

import java.time.Instant;

public record CustomerResponseDTO(
    Long id,
    String name,
    String email,
    String phone,
    String company,
    CustomerStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static CustomerResponseDTO from(Customer customer) {
        return new CustomerResponseDTO(
            customer.getId(),
            customer.getName(),
            customer.getEmail(),
            customer.getPhone(),
            customer.getCompany(),
            customer.getStatus(),
            customer.getCreatedAt(),
            customer.getUpdatedAt()
        );
    }
}
