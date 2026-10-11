package com.jessica.crm_vendas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.jessica.crm_vendas.model.InboundLead;

public interface InboundLeadRepository extends JpaRepository<InboundLead, Long> {

    Optional<InboundLead> findByOrganizationIdAndProviderAndExternalId(Long organizationId, String provider, String externalId);

    @EntityGraph(attributePaths = {"customer", "deal"})
    List<InboundLead> findByOrganizationIdOrderByReceivedAtDesc(Long organizationId, Pageable pageable);

    @Modifying
    @Query("delete from InboundLead l where l.organizationId = :organizationId")
    void deleteAllOfOrganization(Long organizationId);
}
