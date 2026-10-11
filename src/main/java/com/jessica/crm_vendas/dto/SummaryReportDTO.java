package com.jessica.crm_vendas.dto;

import java.math.BigDecimal;

/** Indicadores do topo do funil. */
public record SummaryReportDTO(
    long openCount,
    BigDecimal openValue,
    long wonThisMonthCount,
    BigDecimal wonThisMonthValue,
    BigDecimal wonLastMonthValue,
    /** Ganhos / (ganhos + perdidos) nos últimos 90 dias, de 0 a 1; nulo sem negócios fechados. */
    Double conversionRate,
    long stalledCount,
    int stalledDays
) {}
