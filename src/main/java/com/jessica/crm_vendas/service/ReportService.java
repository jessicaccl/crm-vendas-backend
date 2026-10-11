package com.jessica.crm_vendas.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jessica.crm_vendas.dto.SummaryReportDTO;
import com.jessica.crm_vendas.model.DealStage;
import com.jessica.crm_vendas.repository.DealRepository;
import com.jessica.crm_vendas.repository.DealTotals;
import com.jessica.crm_vendas.security.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {

    /** Negócio aberto sem mudar de etapa há mais que isso é considerado parado. */
    static final int STALLED_DAYS = 7;
    static final int CONVERSION_WINDOW_DAYS = 90;

    private final DealRepository repository;
    private final Clock clock;
    private final TenantContext tenant;

    @Transactional(readOnly = true)
    public SummaryReportDTO summary() {
        Long org = tenant.organizationId();
        ZonedDateTime now = ZonedDateTime.now(clock);
        Instant monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.getZone()).toInstant();
        Instant lastMonthStart = now.toLocalDate().withDayOfMonth(1).minusMonths(1).atStartOfDay(now.getZone()).toInstant();
        Instant windowStart = now.minusDays(CONVERSION_WINDOW_DAYS).toInstant();
        Instant end = now.toInstant();

        DealTotals open = repository.totalsByStages(org, DealStage.OPEN);
        DealTotals wonThisMonth = repository.totalsClosedBetween(org, DealStage.WON, monthStart, end);
        DealTotals wonLastMonth = repository.totalsClosedBetween(org, DealStage.WON, lastMonthStart, monthStart);
        long wonInWindow = repository.totalsClosedBetween(org, DealStage.WON, windowStart, end).count();
        long lostInWindow = repository.totalsClosedBetween(org, DealStage.LOST, windowStart, end).count();
        long stalled = repository.countByOrganizationIdAndStageInAndStageChangedAtBefore(
                org, DealStage.OPEN, now.minusDays(STALLED_DAYS).toInstant());

        long closedInWindow = wonInWindow + lostInWindow;
        Double conversion = closedInWindow == 0 ? null : (double) wonInWindow / closedInWindow;

        return new SummaryReportDTO(
                open.count(),
                open.value(),
                wonThisMonth.count(),
                wonThisMonth.value(),
                wonLastMonth.value(),
                conversion,
                stalled,
                STALLED_DAYS);
    }
}
