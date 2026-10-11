package com.jessica.crm_vendas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jessica.crm_vendas.model.DealSource;
import com.jessica.crm_vendas.model.DealStage;

import jakarta.validation.constraints.*;

public record DealRequestDTO(
    @NotBlank(message = "Informe o título") @Size(max = 150, message = "Use no máximo 150 caracteres") String title,
    @NotNull(message = "Escolha o cliente") Long customerId,
    @NotNull(message = "Informe o valor")
    @PositiveOrZero(message = "O valor não pode ser negativo")
    @Digits(integer = 12, fraction = 2, message = "Valor inválido") BigDecimal value,
    DealStage stage,
    DealSource source,
    LocalDate expectedCloseDate,
    @Size(max = 255, message = "Use no máximo 255 caracteres") String lostReason
) {}
