package com.jessica.crm_vendas.dto;

/**
 * Situação das integrações da organização logada.
 * available = false na Demo; netlifyConfigured = já existe chave para o webhook.
 */
public record IntegrationStatusDTO(boolean available, boolean netlifyConfigured, String netlifyWebhookPath) {}
