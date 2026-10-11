package com.jessica.crm_vendas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jessica.crm_vendas.dto.SummaryReportDTO;
import com.jessica.crm_vendas.model.DealStage;
import com.jessica.crm_vendas.repository.DealRepository;
import com.jessica.crm_vendas.repository.DealTotals;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // 15/10/2026 12:00 em Brasília
    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");
    private static final Instant OCTOBER_START = Instant.parse("2026-10-01T03:00:00Z");
    private static final Instant SEPTEMBER_START = Instant.parse("2026-09-01T03:00:00Z");

    @Mock
    private DealRepository repository;

    @Test
    void summaryUsesBusinessTimezoneForMonthBoundaries() {
        ReportService service = new ReportService(repository, Clock.fixed(NOW, SAO_PAULO));

        when(repository.totalsByStages(DealStage.OPEN)).thenReturn(new DealTotals(8L, new BigDecimal("111900")));
        when(repository.totalsClosedBetween(DealStage.WON, OCTOBER_START, NOW))
                .thenReturn(new DealTotals(2L, new BigDecimal("30800")));
        when(repository.totalsClosedBetween(DealStage.WON, SEPTEMBER_START, OCTOBER_START))
                .thenReturn(new DealTotals(2L, new BigDecimal("24200")));
        when(repository.totalsClosedBetween(eq(DealStage.WON), eq(NOW.minusSeconds(90L * 86_400)), eq(NOW)))
                .thenReturn(new DealTotals(3L, new BigDecimal("40000")));
        when(repository.totalsClosedBetween(eq(DealStage.LOST), any(), eq(NOW)))
                .thenReturn(new DealTotals(1L, new BigDecimal("8900")));
        when(repository.countByStageInAndStageChangedAtBefore(eq(DealStage.OPEN), any())).thenReturn(3L);

        SummaryReportDTO summary = service.summary();

        assertThat(summary.openCount()).isEqualTo(8);
        assertThat(summary.openValue()).isEqualByComparingTo("111900");
        assertThat(summary.wonThisMonthValue()).isEqualByComparingTo("30800");
        assertThat(summary.wonLastMonthValue()).isEqualByComparingTo("24200");
        assertThat(summary.conversionRate()).isEqualTo(0.75);
        assertThat(summary.stalledCount()).isEqualTo(3);
    }

    @Test
    void conversionIsNullWithoutClosedDeals() {
        ReportService service = new ReportService(repository, Clock.fixed(NOW, SAO_PAULO));
        DealTotals empty = new DealTotals(0L, null);
        when(repository.totalsByStages(any())).thenReturn(empty);
        when(repository.totalsClosedBetween(any(), any(), any())).thenReturn(empty);

        SummaryReportDTO summary = service.summary();

        assertThat(summary.conversionRate()).isNull();
        assertThat(summary.openValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
