package com.kbase.ai.repository;

import java.util.Optional;
import java.util.UUID;

import com.kbase.ai.entity.AiGuideSource;
import com.kbase.ai.enums.AiGuideSourceStatus;

import org.springframework.data.jpa.repository.JpaRepository;

/** Allowlisted Guide source metadata persistence. */
public interface AiGuideSourceRepository extends JpaRepository<AiGuideSource, UUID> {

    Optional<AiGuideSource> findBySourceKey(String sourceKey);

    boolean existsBySourceKey(String sourceKey);

    long countByStatus(AiGuideSourceStatus status);
}
