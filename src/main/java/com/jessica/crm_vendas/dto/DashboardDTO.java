package com.jessica.crm_vendas.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jessica.crm_vendas.model.DealSource;
import com.jessica.crm_vendas.model.DealStage;

/**
 * Painel de resultados da organização.
 *
 * @param windowDays          janela (em dias) usada no funil, nas origens e nos motivos de perda
 * @param revenueByMonth      receita ganha nos últimos meses, do mais antigo ao atual
 * @param funnel              quantos negócios que entraram na janela chegaram a cada etapa
 * @param sources             negócios que entraram na janela, por origem
 * @param forecast            valor em aberto ponderado pela chance de cada etapa
 * @param averageCycleDays    dias, em média, da entrada no funil até o ganho (negócios ganhos na janela)
 * @param averageTicket       valor médio dos negócios ganhos na janela
 * @param lostReasons         motivos de perda mais comuns na janela
 * @param stalled             negócios abertos sem mudar de etapa há 7+ dias (os mais parados primeiro)
 */
public record DashboardDTO(
    int windowDays,
    List<MonthRevenue> revenueByMonth,
    List<FunnelStep> funnel,
    List<SourceTotal> sources,
    Forecast forecast,
    Double averageCycleDays,
    BigDecimal averageTicket,
    List<LostReason> lostReasons,
    List<StalledDeal> stalled
) {
    /** month no formato 2026-10. */
    public record MonthRevenue(String month, long count, BigDecimal value) {}

    /** reached = negócios que chegaram a esta etapa ou além; rate = reached / entraram no funil. */
    public record FunnelStep(DealStage stage, long reached, double rate) {}

    public record SourceTotal(DealSource source, long deals, long won, BigDecimal wonValue) {}

    public record Forecast(BigDecimal weightedValue, BigDecimal openValue, List<StageForecast> byStage) {}

    public record StageForecast(DealStage stage, double probability, long deals, BigDecimal value, BigDecimal weighted) {}

    public record LostReason(String reason, long count) {}

    public record StalledDeal(Long id, String title, String customer, DealStage stage, BigDecimal value, long days) {}
}
