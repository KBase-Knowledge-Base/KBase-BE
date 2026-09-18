package com.kbase.project.repository;

import com.kbase.project.entity.Project;
import com.kbase.project.enums.ProjectRole;

/**
 * Read-only view of a membership row for "my projects" listing: the project
 * plus the caller's role, without loading the full membership graph.
 */
public interface ProjectMembershipProjection {

    Project getProject();

    ProjectRole getRole();
}
