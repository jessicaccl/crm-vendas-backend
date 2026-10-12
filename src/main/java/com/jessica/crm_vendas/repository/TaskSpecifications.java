package com.jessica.crm_vendas.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.jessica.crm_vendas.model.Task;

import jakarta.persistence.criteria.Predicate;

public final class TaskSpecifications {

    private TaskSpecifications() {}

    /** Tarefas da organização, filtradas por situação (pendente/concluída), cliente e negócio. */
    public static Specification<Task> search(Long organizationId, Boolean done, Long customerId, Long dealId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));
            if (done != null) {
                predicates.add(done ? cb.isNotNull(root.get("doneAt")) : cb.isNull(root.get("doneAt")));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (dealId != null) {
                predicates.add(cb.equal(root.get("deal").get("id"), dealId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
