package com.jessica.crm_vendas.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.jessica.crm_vendas.model.Deal;
import com.jessica.crm_vendas.model.DealStage;

/** Todas as consultas recebem a organização: um negócio nunca aparece para outra empresa. */
public interface DealRepository extends JpaRepository<Deal, Long>, JpaSpecificationExecutor<Deal> {

    @Override
    @EntityGraph(attributePaths = "customer")
    List<Deal> findAll(Specification<Deal> spec, Sort sort);

    Optional<Deal> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByCustomerId(Long customerId);

    @Query("select new com.jessica.crm_vendas.repository.DealTotals(count(d), sum(d.value)) "
            + "from Deal d where d.organizationId = :organizationId and d.stage in :stages")
    DealTotals totalsByStages(Long organizationId, Collection<DealStage> stages);

    @Query("select new com.jessica.crm_vendas.repository.DealTotals(count(d), sum(d.value)) "
            + "from Deal d where d.organizationId = :organizationId and d.stage = :stage "
            + "and d.closedAt >= :from and d.closedAt < :to")
    DealTotals totalsClosedBetween(Long organizationId, DealStage stage, Instant from, Instant to);

    long countByOrganizationIdAndStageInAndStageChangedAtBefore(Long organizationId, Collection<DealStage> stages, Instant before);

    /** O histórico de etapas sai junto pelo "on delete cascade" do banco. */
    @Modifying
    @Query("delete from Deal d where d.organizationId = :organizationId")
    void deleteAllOfOrganization(Long organizationId);
}
