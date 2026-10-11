package com.jessica.crm_vendas.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.jessica.crm_vendas.model.Customer;
import com.jessica.crm_vendas.model.CustomerStatus;

import jakarta.persistence.criteria.Predicate;

public final class CustomerSpecifications {

    private CustomerSpecifications() {}

    /** Busca por texto (nome, e-mail ou empresa) e filtro opcional de status. */
    public static Specification<Customer> search(String text, CustomerStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (text != null && !text.isBlank()) {
                String like = "%" + text.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("company")), like)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
