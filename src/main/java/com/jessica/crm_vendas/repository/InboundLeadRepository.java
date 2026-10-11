package com.jessica.crm_vendas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jessica.crm_vendas.model.InboundLead;

public interface InboundLeadRepository extends JpaRepository<InboundLead, Long> {

    Optional<InboundLead> findByProviderAndExternalId(String provider, String externalId);

    @EntityGraph(attributePaths = {"customer", "deal"})
    List<InboundLead> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
