package com.kbase.ai.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.ai.entity.AiConversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Project/user-scoped conversation persistence queries. */
public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    long countByProjectIdAndCreatedByUserId(UUID projectId, UUID createdByUserId);

    Optional<AiConversation> findByIdAndProjectIdAndCreatedByUserId(
            UUID conversationId, UUID projectId, UUID createdByUserId);

    List<AiConversation> findAllByProjectIdAndCreatedByUserIdOrderByUpdatedAtDesc(
            UUID projectId, UUID createdByUserId);

    Page<AiConversation> findByProjectIdAndCreatedByUserId(
            UUID projectId, UUID createdByUserId, Pageable pageable);

    /** Bulk lifecycle delete; child messages and citation snapshots cascade in PostgreSQL. */
    @Modifying
    @Query("delete from AiConversation c where c.project.id = :projectId "
            + "and c.createdByUser.id = :userId")
    int deleteByProjectIdAndCreatedByUserId(@Param("projectId") UUID projectId,
            @Param("userId") UUID userId);
}
