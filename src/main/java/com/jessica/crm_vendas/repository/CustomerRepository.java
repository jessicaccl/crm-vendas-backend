package com.jessica.crm_vendas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.jessica.crm_vendas.model.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, Long id);

	Optional<Customer> findFirstByEmail(String email);

	/** Procura pelo telefone ignorando a formatação: "(21) 99603-2769" e "21996032769" batem. */
	@Query(nativeQuery = true, value =
			"select * from tb_customers where regexp_replace(coalesce(phone, ''), '\\D', '', 'g') = :digits order by id limit 1")
	Optional<Customer> findFirstByPhoneDigits(String digits);
}
