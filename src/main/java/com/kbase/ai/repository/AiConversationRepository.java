package com.kbase.ai.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.ai.entity.AiConversation;

import org.springframework.data.jpa.repository.JpaRepository;

/** Project/user-scoped conversation persistence queries. */
public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    long countByProjectIdAndCreatedByUserId(UUID projectId, UUID createdByUserId);

    Optional<AiConversation> findByIdAndProjectIdAndCreatedByUserId(
            UUID conversationId, UUID projectId, UUID createdByUserId);

    List<AiConversation> findAllByProjectIdAndCreatedByUserIdOrderByUpdatedAtDesc(
            UUID projectId, UUID createdByUserId);
}
