package com.jessica.crm_vendas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.jessica.crm_vendas.model.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
	boolean existsByEmail(String email);
}