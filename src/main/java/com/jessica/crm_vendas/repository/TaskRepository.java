package com.jessica.crm_vendas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.jessica.crm_vendas.model.Task;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @Override
    @EntityGraph(attributePaths = {"customer", "deal"})
    List<Task> findAll(Specification<Task> spec, Sort sort);

    Optional<Task> findByIdAndOrganizationId(Long id, Long organizationId);

    @EntityGraph(attributePaths = "deal")
    List<Task> findByOrganizationIdAndCustomerIdAndDoneAtIsNotNull(Long organizationId, Long customerId);

    @Modifying
    @Query("delete from Task t where t.organizationId = :organizationId")
    void deleteAllOfOrganization(Long organizationId);
}
