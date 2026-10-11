package com.jessica.crm_vendas.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.jessica.crm_vendas.model.Deal;
import com.jessica.crm_vendas.model.DealStage;

public interface DealRepository extends JpaRepository<Deal, Long>, JpaSpecificationExecutor<Deal> {

    @Override
    @EntityGraph(attributePaths = "customer")
    List<Deal> findAll(Specification<Deal> spec, Sort sort);

    boolean existsByCustomerId(Long customerId);

    @Query("select new com.jessica.crm_vendas.repository.DealTotals(count(d), sum(d.value)) "
            + "from Deal d where d.stage in :stages")
    DealTotals totalsByStages(Collection<DealStage> stages);

    @Query("select new com.jessica.crm_vendas.repository.DealTotals(count(d), sum(d.value)) "
            + "from Deal d where d.stage = :stage and d.closedAt >= :from and d.closedAt < :to")
    DealTotals totalsClosedBetween(DealStage stage, Instant from, Instant to);

    long countByStageInAndStageChangedAtBefore(Collection<DealStage> stages, Instant before);
}
