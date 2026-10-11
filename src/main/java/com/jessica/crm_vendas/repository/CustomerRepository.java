package com.jessica.crm_vendas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.jessica.crm_vendas.model.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, Long id);
}
