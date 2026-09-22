package com.kbase.ai.repository;

import java.util.List;
import java.util.UUID;

import com.kbase.ai.entity.AiMessage;
import com.kbase.ai.enums.AiGenerationStatus;
import com.kbase.ai.enums.AiMessageRole;

import org.springframework.data.jpa.repository.JpaRepository;

/** Message persistence queries; generation orchestration is outside this slice. */
public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    List<AiMessage> findAllByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);

    boolean existsByConversationIdAndRoleAndGenerationStatus(
            UUID conversationId, AiMessageRole role, AiGenerationStatus generationStatus);
}
