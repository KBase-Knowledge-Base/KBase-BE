package com.kbase.project.repository;

import java.util.UUID;

import com.kbase.project.entity.Project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence queries for project records. */
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    /**
     * Removes the project row directly through SQL so the Flyway FK cascades
     * stay the authoritative child-row cleanup. A bulk statement is required
     * because a managed membership referencing the deleted project makes a
     * managed-entity remove fail Hibernate 7 flush validation.
     */
    @Modifying
    @Query("delete from Project p where p.id = :id")
    void deleteProjectCascade(@Param("id") UUID id);
}
