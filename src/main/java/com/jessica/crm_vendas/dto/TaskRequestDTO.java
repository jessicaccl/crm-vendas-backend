package com.jessica.crm_vendas.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Tarefa ligada a um cliente e/ou negócio; informando só o negócio, o cliente é o dele. */
public record TaskRequestDTO(
    @NotBlank(message = "Descreva a tarefa") @Size(max = 200, message = "Use no máximo 200 caracteres") String title,
    @NotNull(message = "Informe a data") Instant dueAt,
    Long customerId,
    Long dealId
) {}
