package com.jessica.crm_vendas.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Empresa que usa o CRM. Todos os dados de negócio pertencem a uma organização. */
@Entity
@Table(name = "tb_organizations")
@Getter @Setter
@NoArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Identificador curto usado em URLs, ex.: o endereço do webhook. */
    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    /** Organização de demonstração: dados fictícios, restaurados periodicamente, sem integrações. */
    @Column(nullable = false)
    private boolean demo;

    /** Segredo que assina os webhooks do Netlify desta organização; nulo = integração desligada. */
    @Column(length = 100)
    private String netlifyWebhookSecret;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    public Organization(String name, String slug, boolean demo) {
        this.name = name;
        this.slug = slug;
        this.demo = demo;
    }
}
