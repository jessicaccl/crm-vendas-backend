package com.jessica.crm_vendas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jessica.crm_vendas.dto.DashboardDTO;
import com.jessica.crm_vendas.model.Deal;
import com.jessica.crm_vendas.model.DealSource;
import com.jessica.crm_vendas.model.DealStage;
import com.jessica.crm_vendas.model.DealStageChange;
import com.jessica.crm_vendas.repository.DealRepository;
import com.jessica.crm_vendas.repository.DealStageChangeRepository;
import com.jessica.crm_vendas.security.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * Painel de resultados: receita por mês, funil de conversão, origens, previsão e perdas.
 * Para o volume de uma pequena empresa, os cálculos são feitos em memória sobre os negócios
 * da organização (uma consulta para negócios e outra para o histórico de etapas).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    static final int WINDOW_DAYS = 90;
    static final int MONTHS = 6;
    static final int STALLED_DAYS = ReportService.STALLED_DAYS;

    /** Ordem do funil (Perdido fica de fora: é saída, não etapa). */
    private static final List<DealStage> FUNNEL = List.of(DealStage.PROSPECT, DealStage.QUALIFIED, DealStage.PROPOSAL, DealStage.WON);

    /** Chance de fechar a partir de cada etapa aberta, usada na previsão ponderada. */
    static final Map<DealStage, Double> WIN_PROBABILITY = Map.of(
            DealStage.PROSPECT, 0.10,
            DealStage.QUALIFIED, 0.30,
            DealStage.PROPOSAL, 0.60);

    private final DealRepository dealRepository;
    private final DealStageChangeRepository stageChangeRepository;
    private final TenantContext tenant;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardDTO dashboard() {
        Long org = tenant.organizationId();
        List<Deal> deals = dealRepository.findByOrganizationId(org);
        Map<Long, List<DealStageChange>> history = stageChangeRepository.findAllOfOrganization(org).stream()
                .collect(Collectors.groupingBy(change -> change.getDeal().getId()));
        return build(deals, history, ZonedDateTime.now(clock));
    }

    /** Separado de {@link #dashboard()} para ser testado sem banco. */
    static DashboardDTO build(List<Deal> deals, Map<Long, List<DealStageChange>> history, ZonedDateTime now) {
        ZoneId zone = now.getZone();
        Instant end = now.toInstant();
        Instant windowStart = now.minusDays(WINDOW_DAYS).toInstant();

        Map<Long, Instant> enteredAt = new HashMap<>();
        Map<Long, DealStage> furthest = new HashMap<>();
        for (Deal deal : deals) {
            List<DealStageChange> changes = history.getOrDefault(deal.getId(), List.of());
            enteredAt.put(deal.getId(), changes.stream().map(DealStageChange::getChangedAt)
                    .min(Comparator.naturalOrder()).orElse(deal.getStageChangedAt()));
            furthest.put(deal.getId(), furthestStage(deal, changes));
        }

        List<Deal> entered = deals.stream().filter(deal -> !enteredAt.get(deal.getId()).isBefore(windowStart)).toList();
        List<Deal> wonInWindow = deals.stream()
                .filter(deal -> deal.getStage() == DealStage.WON && closedBetween(deal, windowStart, end)).toList();

        return new DashboardDTO(
                WINDOW_DAYS,
                revenueByMonth(deals, now, zone),
                funnel(entered, furthest),
                sources(entered),
                forecast(deals),
                averageCycleDays(wonInWindow, enteredAt),
                average(wonInWindow.stream().map(Deal::getValue).toList()),
                lostReasons(deals, windowStart, end),
                stalled(deals, end));
    }

    /** Etapa mais avançada que o negócio já alcançou (um negócio perdido em Proposta "chegou" a Proposta). */
    private static DealStage furthestStage(Deal deal, List<DealStageChange> changes) {
        DealStage furthest = deal.getStage() == DealStage.LOST ? DealStage.PROSPECT : deal.getStage();
        for (DealStageChange change : changes) {
            DealStage stage = change.getToStage();
            if (stage != DealStage.LOST && FUNNEL.indexOf(stage) > FUNNEL.indexOf(furthest)) {
                furthest = stage;
            }
        }
        return furthest;
    }

    private static List<DashboardDTO.MonthRevenue> revenueByMonth(List<Deal> deals, ZonedDateTime now, ZoneId zone) {
        List<DashboardDTO.MonthRevenue> months = new ArrayList<>();
        YearMonth current = YearMonth.from(now);
        for (int i = MONTHS - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
            Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
            List<Deal> won = deals.stream()
                    .filter(deal -> deal.getStage() == DealStage.WON && closedBetween(deal, from, to)).toList();
            months.add(new DashboardDTO.MonthRevenue(month.toString(), won.size(), sum(won.stream().map(Deal::getValue).toList())));
        }
        return months;
    }

    private static List<DashboardDTO.FunnelStep> funnel(List<Deal> entered, Map<Long, DealStage> furthest) {
        long total = entered.size();
        return FUNNEL.stream().map(stage -> {
            long reached = entered.stream()
                    .filter(deal -> FUNNEL.indexOf(furthest.get(deal.getId())) >= FUNNEL.indexOf(stage))
                    .count();
            return new DashboardDTO.FunnelStep(stage, reached, total == 0 ? 0 : round((double) reached / total, 3));
        }).toList();
    }

    private static List<DashboardDTO.SourceTotal> sources(List<Deal> entered) {
        Map<DealSource, List<Deal>> bySource = entered.stream().collect(Collectors.groupingBy(
                deal -> deal.getSource() == null ? DealSource.OTHER : deal.getSource(),
                () -> new EnumMap<>(DealSource.class), Collectors.toList()));
        return bySource.entrySet().stream()
                .map(entry -> {
                    List<Deal> won = entry.getValue().stream().filter(deal -> deal.getStage() == DealStage.WON).toList();
                    return new DashboardDTO.SourceTotal(entry.getKey(), entry.getValue().size(), won.size(),
                            sum(won.stream().map(Deal::getValue).toList()));
                })
                .sorted(Comparator.comparingLong(DashboardDTO.SourceTotal::deals).reversed())
                .toList();
    }

    private static DashboardDTO.Forecast forecast(List<Deal> deals) {
        List<DashboardDTO.StageForecast> byStage = new ArrayList<>();
        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal openTotal = BigDecimal.ZERO;
        for (DealStage stage : List.of(DealStage.PROSPECT, DealStage.QUALIFIED, DealStage.PROPOSAL)) {
            List<Deal> inStage = deals.stream().filter(deal -> deal.getStage() == stage).toList();
            BigDecimal value = sum(inStage.stream().map(Deal::getValue).toList());
            double probability = WIN_PROBABILITY.get(stage);
            BigDecimal weighted = value.multiply(BigDecimal.valueOf(probability)).setScale(2, RoundingMode.HALF_UP);
            byStage.add(new DashboardDTO.StageForecast(stage, probability, inStage.size(), value, weighted));
            weightedTotal = weightedTotal.add(weighted);
            openTotal = openTotal.add(value);
        }
        return new DashboardDTO.Forecast(weightedTotal, openTotal, byStage);
    }

    private static Double averageCycleDays(List<Deal> won, Map<Long, Instant> enteredAt) {
        if (won.isEmpty()) return null;
        double averageHours = won.stream()
                .mapToLong(deal -> Duration.between(enteredAt.get(deal.getId()), deal.getClosedAt()).toHours())
                .average().orElse(0);
        return round(averageHours / 24.0, 1);
    }

    private static List<DashboardDTO.LostReason> lostReasons(List<Deal> deals, Instant from, Instant to) {
        Map<String, Long> counts = deals.stream()
                .filter(deal -> deal.getStage() == DealStage.LOST && closedBetween(deal, from, to))
                .map(deal -> deal.getLostReason() == null || deal.getLostReason().isBlank() ? "Sem motivo informado" : deal.getLostReason().trim())
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> new DashboardDTO.LostReason(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(DashboardDTO.LostReason::count).reversed())
                .limit(5)
                .toList();
    }

    private static List<DashboardDTO.StalledDeal> stalled(List<Deal> deals, Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(STALLED_DAYS));
        return deals.stream()
                .filter(deal -> DealStage.OPEN.contains(deal.getStage()) && deal.getStageChangedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(Deal::getStageChangedAt))
                .limit(8)
                .map(deal -> new DashboardDTO.StalledDeal(deal.getId(), deal.getTitle(),
                        deal.getCustomer().getCompany() != null ? deal.getCustomer().getCompany() : deal.getCustomer().getName(),
                        deal.getStage(), deal.getValue(), Duration.between(deal.getStageChangedAt(), now).toDays()))
                .toList();
    }

    private static boolean closedBetween(Deal deal, Instant from, Instant to) {
        return deal.getClosedAt() != null && !deal.getClosedAt().isBefore(from) && deal.getClosedAt().isBefore(to);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        return sum(values).divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
