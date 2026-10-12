package com.jessica.crm_vendas.dto;

import com.jessica.crm_vendas.model.ActivityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActivityRequestDTO(
    @NotNull(message = "Escolha o tipo") ActivityType type,
    @NotBlank(message = "Escreva o que aconteceu") @Size(max = 5000, message = "Use no máximo 5000 caracteres") String content,
    Long dealId
) {}
