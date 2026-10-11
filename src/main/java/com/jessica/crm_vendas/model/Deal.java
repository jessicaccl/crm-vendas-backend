package com.jessica.crm_vendas.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tb_deals")
@Getter @Setter
@NoArgsConstructor
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long organizationId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal value = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealStage stage = DealStage.PROSPECT;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DealSource source;

    private LocalDate expectedCloseDate;

    private String lostReason;

    /** Observações livres (ex.: resultado do diagnóstico que originou o lead). */
    @Column(columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Quando o negócio entrou na etapa atual (usado para "parado há X dias"). */
    @Column(nullable = false)
    private Instant stageChangedAt;

    /** Quando foi ganho ou perdido; nulo enquanto está em aberto. */
    private Instant closedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
