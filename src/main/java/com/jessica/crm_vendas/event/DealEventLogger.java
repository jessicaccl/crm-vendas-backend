package com.jessica.crm_vendas.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Registra no log cada mudança de etapa confirmada no banco. */
@Component
public class DealEventLogger {

    private static final Logger log = LoggerFactory.getLogger(DealEventLogger.class);

    @TransactionalEventListener
    public void onStageChanged(DealStageChangedEvent event) {
        log.info("Negócio {} mudou de etapa: {} -> {}", event.dealId(), event.fromStage(), event.toStage());
    }
}
