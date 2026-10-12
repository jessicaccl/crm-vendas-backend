package com.jessica.crm_vendas.dto;

import java.time.Instant;

/**
 * Um acontecimento na linha do tempo do cliente.
 *
 * @param kind       NOTE, CALL, WHATSAPP, EMAIL, MEETING (atividades registradas),
 *                   STAGE_CHANGE (negócio mudou de etapa), LEAD_RECEIVED (lead de integração)
 *                   ou TASK_DONE (tarefa concluída)
 * @param activityId preenchido só para atividades registradas (podem ser excluídas)
 */
public record TimelineItemDTO(
    String kind,
    String title,
    String detail,
    Instant at,
    Long dealId,
    String dealTitle,
    String author,
    Long activityId
) {}
