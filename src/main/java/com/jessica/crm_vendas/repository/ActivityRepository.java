package com.jessica.crm_vendas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.jessica.crm_vendas.model.Activity;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    @EntityGraph(attributePaths = "deal")
    List<Activity> findByOrganizationIdAndCustomerIdOrderByCreatedAtDesc(Long organizationId, Long customerId);

    Optional<Activity> findByIdAndOrganizationId(Long id, Long organizationId);

    @Modifying
    @Query("delete from Activity a where a.organizationId = :organizationId")
    void deleteAllOfOrganization(Long organizationId);
}
