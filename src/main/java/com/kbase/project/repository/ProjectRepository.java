package com.kbase.project.repository;

import java.util.UUID;

import com.kbase.project.entity.Project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence queries for project records. */
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {
}
