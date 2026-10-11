package com.jessica.crm_vendas.repository;

import java.math.BigDecimal;

/** Quantidade e soma de valores de um grupo de negócios (sum é nulo quando não há linhas). */
public record DealTotals(Long count, BigDecimal sum) {

    public BigDecimal value() {
        return sum == null ? BigDecimal.ZERO : sum;
    }
}
