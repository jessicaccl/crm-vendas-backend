package com.jessica.crm_vendas.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Lead recebido de uma integração externa, guardado com o conteúdo original. */
@Entity
@Table(name = "tb_inbound_leads")
@Getter @Setter
@NoArgsConstructor
public class InboundLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String provider;

    /** ID do envio no sistema de origem; impede processar o mesmo envio duas vezes. */
    @Column(nullable = false, length = 100)
    private String externalId;

    @Column(length = 100)
    private String formName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @Column(nullable = false)
    private Instant receivedAt;
}
