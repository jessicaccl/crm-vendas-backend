package com.jessica.crm_vendas.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.Deal;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

public final class DealSpecifications {

    private DealSpecifications() {}

    /** Busca por texto (título, nome do contato ou empresa) e filtro opcional por cliente. */
    public static Specification<Deal> search(String text, Long customerId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Deal, Customer> customer = root.join("customer");

            if (customerId != null) {
                predicates.add(cb.equal(customer.get("id"), customerId));
            }

            if (text != null && !text.isBlank()) {
                String like = "%" + text.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(customer.get("name")), like),
                    cb.like(cb.lower(customer.get("company")), like)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
