package com.jessica.crm_vendas.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jessica.crm_vendas.dto.SummaryReportDTO;
import com.jessica.crm_vendas.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Relatórios", description = "Indicadores do funil")
public class ReportController {

    private final ReportService service;

    @GetMapping("/summary")
    @Operation(summary = "Indicadores: em aberto, ganho no mês, conversão em 90 dias e negócios parados")
    public SummaryReportDTO summary() {
        return service.summary();
    }
}
