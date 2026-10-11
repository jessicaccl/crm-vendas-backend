package com.jessica.crm_vendas.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Registro de cada mudança de etapa; base para as métricas de conversão do painel. */
@Entity
@Table(name = "tb_deal_stage_changes")
@Getter @Setter
@NoArgsConstructor
public class DealStageChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DealStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealStage toStage;

    @Column(nullable = false)
    private Instant changedAt;

    public DealStageChange(Deal deal, DealStage fromStage, DealStage toStage, Instant changedAt) {
        this.deal = deal;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.changedAt = changedAt;
    }
}
