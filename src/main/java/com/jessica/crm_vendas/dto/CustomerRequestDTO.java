package com.jessica.crm_vendas.dto;

import jakarta.validation.constraints.*;

public record CustomerRequestDTO(
    @NotBlank String name,
    @Email String email,
    String phone,
    String status
) {}