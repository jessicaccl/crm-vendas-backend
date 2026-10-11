package com.jessica.crm_vendas.event;

import java.time.Instant;

import com.jessica.crm_vendas.model.DealStage;

/**
 * Publicado sempre que um negócio entra em uma etapa (inclusive ao ser criado).
 * Ponto de extensão para integrações: na fase 5 um listener envia webhooks com este evento.
 */
public record DealStageChangedEvent(Long dealId, DealStage fromStage, DealStage toStage, Instant changedAt) {}
