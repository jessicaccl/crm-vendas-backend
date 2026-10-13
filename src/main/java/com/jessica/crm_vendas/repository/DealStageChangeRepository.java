package com.jessica.crm_vendas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.jessica.crm_vendas.model.DealStageChange;

public interface DealStageChangeRepository extends JpaRepository<DealStageChange, Long> {

    /** Histórico de etapas de todos os negócios de um cliente, para a linha do tempo. */
    @Query("select c from DealStageChange c join fetch c.deal d "
            + "where d.organizationId = :organizationId and d.customer.id = :customerId order by c.changedAt desc")
    List<DealStageChange> findTimeline(Long organizationId, Long customerId);

    /** Todo o histórico de etapas da organização, para o painel de resultados. */
    @Query("select c from DealStageChange c join c.deal d where d.organizationId = :organizationId")
    List<DealStageChange> findAllOfOrganization(Long organizationId);
}
