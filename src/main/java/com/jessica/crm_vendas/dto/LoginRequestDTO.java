package com.jessica.crm_vendas.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
    @NotBlank(message = "Informe o e-mail") String email,
    @NotBlank(message = "Informe a senha") String password
) {}
