package com.kbase.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.auth.entity.RefreshSession;

import org.springframework.data.jpa.repository.JpaRepository;

/** PostgreSQL-backed refresh-session lifecycle queries. */
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    List<RefreshSession> findAllByUserId(UUID userId);

    long deleteByUserId(UUID userId);

    long deleteByExpiresAtBefore(Instant cutoff);
}
