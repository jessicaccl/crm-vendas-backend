package com.jessica.crm_vendas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jessica.crm_vendas.dto.DashboardDTO;
import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.Deal;
import com.jessica.crm_vendas.model.DealSource;
import com.jessica.crm_vendas.model.DealStage;
import com.jessica.crm_vendas.model.DealStageChange;

class DashboardServiceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // 15/10/2026 12:00 em Brasília
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, SAO_PAULO);

    private final Customer customer = customer();
    private final Map<Long, List<DealStageChange>> history = new HashMap<>();
    private long nextId = 1;

    private static Customer customer() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("Ana");
        customer.setCompany("Ateliê da Ana");
        return customer;
    }

    private Instant daysAgo(int days) {
        return NOW.minusDays(days).toInstant();
    }

    /** Negócio que entrou em Prospecto há {@code enteredDaysAgo} dias e passou pelas etapas informadas. */
    private Deal deal(String value, DealSource source, DealStage finalStage, int enteredDaysAgo, int lastChangeDaysAgo,
                      DealStage... path) {
        Deal deal = new Deal();
        deal.setId(nextId++);
        deal.setTitle("Negócio " + deal.getId());
        deal.setCustomer(customer);
        deal.setValue(new BigDecimal(value));
        deal.setSource(source);
        deal.setStage(finalStage);
        deal.setStageChangedAt(daysAgo(lastChangeDaysAgo));
        if (finalStage.isClosed()) deal.setClosedAt(daysAgo(lastChangeDaysAgo));

        DealStage from = null;
        Instant at = daysAgo(enteredDaysAgo);
        List<DealStageChange> changes = new java.util.ArrayList<>();
        changes.add(new DealStageChange(deal, null, DealStage.PROSPECT, at));
        from = DealStage.PROSPECT;
        for (DealStage stage : path) {
            changes.add(new DealStageChange(deal, from, stage, daysAgo(lastChangeDaysAgo)));
            from = stage;
        }
        history.put(deal.getId(), changes);
        return deal;
    }

    @Test
    void computesFunnelForecastCycleAndRevenue() {
        Deal wonThisMonth = deal("10000", DealSource.REFERRAL, DealStage.WON, 20, 2, DealStage.PROPOSAL, DealStage.WON);
        Deal wonLastMonth = deal("6000", DealSource.INSTAGRAM, DealStage.WON, 50, 40, DealStage.QUALIFIED, DealStage.WON);
        Deal lostInProposal = deal("8000", DealSource.INSTAGRAM, DealStage.LOST, 30, 10, DealStage.PROPOSAL, DealStage.LOST);
        lostInProposal.setLostReason("Preço");
        Deal openProposal = deal("20000", DealSource.WEBSITE, DealStage.PROPOSAL, 15, 12, DealStage.PROPOSAL);
        Deal openProspect = deal("5000", null, DealStage.PROSPECT, 3, 3);
        Deal oldWon = deal("9000", DealSource.REFERRAL, DealStage.WON, 400, 380, DealStage.WON);

        DashboardDTO dashboard = DashboardService.build(
                List.of(wonThisMonth, wonLastMonth, lostInProposal, openProposal, openProspect, oldWon), history, NOW);

        // Receita: seis meses, do mais antigo ao atual
        assertThat(dashboard.revenueByMonth()).hasSize(6);
        assertThat(dashboard.revenueByMonth().get(5).month()).isEqualTo("2026-10");
        assertThat(dashboard.revenueByMonth().get(5).value()).isEqualByComparingTo("10000");
        assertThat(dashboard.revenueByMonth().get(4).value()).isEqualByComparingTo("6000"); // setembro

        // Funil dos 5 negócios que entraram nos últimos 90 dias (o de 400 dias fica de fora).
        // "Chegou à etapa ou além": os 2 ganhos e as 2 propostas (inclusive a perdida) passaram de Qualificado e de Proposta
        assertThat(dashboard.funnel()).extracting(DashboardDTO.FunnelStep::reached).containsExactly(5L, 4L, 4L, 2L);
        assertThat(dashboard.funnel().get(3).rate()).isEqualTo(0.4);

        // Previsão: 20.000 em Proposta (60%) + 5.000 em Prospecto (10%)
        assertThat(dashboard.forecast().weightedValue()).isEqualByComparingTo("12500");
        assertThat(dashboard.forecast().openValue()).isEqualByComparingTo("25000");

        // Ciclo médio dos ganhos na janela: (18 + 10) / 2 dias; ticket médio (10.000 + 6.000) / 2
        assertThat(dashboard.averageCycleDays()).isEqualTo(14.0);
        assertThat(dashboard.averageTicket()).isEqualByComparingTo("8000");

        // Origens: Instagram trouxe 2 negócios, 1 ganho; sem origem conta como "Outro"
        DashboardDTO.SourceTotal instagram = dashboard.sources().get(0);
        assertThat(instagram.source()).isEqualTo(DealSource.INSTAGRAM);
        assertThat(instagram.deals()).isEqualTo(2);
        assertThat(instagram.won()).isEqualTo(1);
        assertThat(dashboard.sources()).extracting(DashboardDTO.SourceTotal::source).contains(DealSource.OTHER);

        assertThat(dashboard.lostReasons()).containsExactly(new DashboardDTO.LostReason("Preço", 1));

        // Parado: Proposta sem mudar há 12 dias
        assertThat(dashboard.stalled()).hasSize(1);
        assertThat(dashboard.stalled().get(0).days()).isEqualTo(12);
        assertThat(dashboard.stalled().get(0).customer()).isEqualTo("Ateliê da Ana");
    }

    @Test
    void emptyOrganizationHasZerosInsteadOfErrors() {
        DashboardDTO dashboard = DashboardService.build(List.of(), Map.of(), NOW);

        assertThat(dashboard.revenueByMonth()).hasSize(6)
                .allSatisfy(month -> assertThat(month.value()).isEqualByComparingTo(BigDecimal.ZERO));
        assertThat(dashboard.funnel()).allSatisfy(step -> assertThat(step.rate()).isZero());
        assertThat(dashboard.averageCycleDays()).isNull();
        assertThat(dashboard.averageTicket()).isNull();
        assertThat(dashboard.forecast().weightedValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void dealCreatedDirectlyAsLostStillCountsAsEnteredTheFunnel() {
        Deal lost = deal("1000", DealSource.WEBSITE, DealStage.LOST, 5, 5, DealStage.LOST);
        DashboardDTO dashboard = DashboardService.build(List.of(lost), history, NOW);
        assertThat(dashboard.funnel()).extracting(DashboardDTO.FunnelStep::reached).containsExactly(1L, 0L, 0L, 0L);
        assertThat(Duration.ofDays(DashboardService.WINDOW_DAYS).toDays()).isEqualTo(90);
    }
}
