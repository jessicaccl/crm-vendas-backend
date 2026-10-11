package com.jessica.crm_vendas.dto;

import com.jessica.crm_vendas.model.DealStage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo do PATCH de etapa: o que o Kanban envia ao soltar um card. */
public record StageChangeRequestDTO(
    @NotNull(message = "Informe a etapa") DealStage stage,
    @Size(max = 255, message = "Use no máximo 255 caracteres") String lostReason
) {}
