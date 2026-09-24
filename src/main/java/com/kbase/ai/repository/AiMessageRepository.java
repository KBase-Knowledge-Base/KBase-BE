package com.kbase.ai.repository;

import java.util.List;
import java.util.UUID;

import com.kbase.ai.entity.AiMessage;
import com.kbase.ai.enums.AiGenerationStatus;
import com.kbase.ai.enums.AiMessageRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Message persistence queries; generation orchestration is outside this slice. */
public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    List<AiMessage> findAllByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);

    boolean existsByConversationIdAndRoleAndGenerationStatus(
            UUID conversationId, AiMessageRole role, AiGenerationStatus generationStatus);

    Page<AiMessage> findByConversationId(UUID conversationId, Pageable pageable);

    @Query("""
            select m from AiMessage m
            where m.conversationId = :conversationId and m.content is not null
              and (m.role = 'USER' or (m.role = 'ASSISTANT' and m.generationStatus = 'COMPLETED'))
            order by m.createdAt desc, m.id desc
            """)
    List<AiMessage> findRecentTextContext(@Param("conversationId") UUID conversationId,
            Pageable pageable);

    java.util.Optional<AiMessage> findByIdAndConversationIdAndRoleAndGenerationStatus(
            UUID id, UUID conversationId, AiMessageRole role, AiGenerationStatus generationStatus);
}
