package com.jessica.crm_vendas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.jessica.crm_vendas.model.Customer;

/** Todas as consultas recebem a organização: um cliente nunca aparece para outra empresa. */
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

	Optional<Customer> findByIdAndOrganizationId(Long id, Long organizationId);

	boolean existsByOrganizationIdAndEmail(Long organizationId, String email);

	boolean existsByOrganizationIdAndEmailAndIdNot(Long organizationId, String email, Long id);

	Optional<Customer> findFirstByOrganizationIdAndEmail(Long organizationId, String email);

	long countByOrganizationId(Long organizationId);

	/** Procura pelo telefone ignorando a formatação: "(21) 99603-2769" e "21996032769" batem. */
	@Query(nativeQuery = true, value = """
			select * from tb_customers
			 where organization_id = :organizationId
			   and regexp_replace(coalesce(phone, ''), '\\D', '', 'g') = :digits
			 order by id limit 1""")
	Optional<Customer> findFirstByPhoneDigits(Long organizationId, String digits);

	@Modifying
	@Query("delete from Customer c where c.organizationId = :organizationId")
	void deleteAllOfOrganization(Long organizationId);
}
