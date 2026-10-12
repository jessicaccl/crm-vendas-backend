package com.jessica.crm_vendas.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Algo que aconteceu com o cliente e foi registrado: anotação, ligação, mensagem, reunião. */
@Entity
@Table(name = "tb_activities")
@Getter @Setter
@NoArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Negócio ao qual a atividade se refere (opcional). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityType type;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 120)
    private String authorName;

    /** Definido pelo serviço (e não pelo Hibernate) para os dados de demonstração poderem ter datas passadas. */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
