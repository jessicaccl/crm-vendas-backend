package com.jessica.crm_vendas.model;

import java.util.EnumSet;
import java.util.Set;

/** Etapas do funil de vendas, na ordem em que aparecem no Kanban. */
public enum DealStage {
    PROSPECT,
    QUALIFIED,
    PROPOSAL,
    WON,
    LOST;

    public static final Set<DealStage> OPEN = EnumSet.of(PROSPECT, QUALIFIED, PROPOSAL);

    public boolean isClosed() {
        return this == WON || this == LOST;
    }
}
