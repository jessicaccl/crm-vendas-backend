package com.jessica.crm_vendas.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Próximo passo com prazo: "Ligar para a Carla na quinta às 14h". */
@Entity
@Table(name = "tb_tasks")
@Getter @Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Instant dueAt;

    /** Quando foi concluída; nulo enquanto está pendente. */
    private Instant doneAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
