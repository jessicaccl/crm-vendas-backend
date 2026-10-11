package com.jessica.crm_vendas.dto;

import com.jessica.crm_vendas.model.CustomerStatus;

import jakarta.validation.constraints.*;

public record CustomerRequestDTO(
    @NotBlank(message = "Informe o nome") @Size(max = 120, message = "Use no máximo 120 caracteres") String name,
    @Email(message = "E-mail inválido") @Size(max = 160, message = "Use no máximo 160 caracteres") String email,
    @Size(max = 30, message = "Use no máximo 30 caracteres") String phone,
    @Size(max = 120, message = "Use no máximo 120 caracteres") String company,
    CustomerStatus status
) {}
