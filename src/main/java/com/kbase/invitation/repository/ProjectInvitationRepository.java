package com.kbase.invitation.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.invitation.entity.ProjectInvitation;
import com.kbase.invitation.enums.InvitationStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Invitation lifecycle and concurrency queries. */
public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, UUID> {

    Optional<ProjectInvitation> findByTokenHash(String tokenHash);

    Optional<ProjectInvitation> findByIdAndProjectId(UUID invitationId, UUID projectId);

    boolean existsByProjectIdAndEmailAndStatus(
            UUID projectId, String email, InvitationStatus status);

    Page<ProjectInvitation> findAllByProjectId(UUID projectId, Pageable pageable);

    Page<ProjectInvitation> findAllByProjectIdAndStatus(
            UUID projectId, InvitationStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i
            from ProjectInvitation i
            where i.tokenHash = :tokenHash
            """)
    Optional<ProjectInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<ProjectInvitation> findAllByStatusAndExpiresAtBefore(
            InvitationStatus status, Instant now);

    boolean existsByInvitedById(UUID userId);
}
