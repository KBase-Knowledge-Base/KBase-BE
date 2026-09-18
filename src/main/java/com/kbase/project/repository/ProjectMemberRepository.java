package com.kbase.project.repository;

import java.util.Optional;
import java.util.UUID;

import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Membership and project-role persistence queries. */
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    Optional<ProjectMember> findByProjectIdAndRole(UUID projectId, ProjectRole role);

    @EntityGraph(attributePaths = "user")
    Page<ProjectMember> findAllByProjectId(UUID projectId, Pageable pageable);

    long deleteByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByUserIdAndRole(UUID userId, ProjectRole role);

    @Query("""
            select pm.project
            from ProjectMember pm
            where pm.user.id = :userId
              and (:role is null or pm.role = :role)
            """)
    Page<Project> findProjectsForUser(
            @Param("userId") UUID userId,
            @Param("role") ProjectRole role,
            Pageable pageable);

    @Query("""
            select pm.project as project, pm.role as role
            from ProjectMember pm
            where pm.user.id = :userId
              and (:role is null or pm.role = :role)
              and (:q = ''
                   or lower(pm.project.name) like lower(concat('%', :q, '%'))
                   or lower(pm.project.description) like lower(concat('%', :q, '%')))
            """)
    Page<ProjectMembershipProjection> findMembershipPageForUser(
            @Param("userId") UUID userId,
            @Param("role") ProjectRole role,
            @Param("q") String q,
            Pageable pageable);
}
