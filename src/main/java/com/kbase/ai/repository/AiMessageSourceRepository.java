package com.kbase.ai.repository;

import java.util.List;
import java.util.UUID;

import com.kbase.ai.entity.AiMessageSource;
import com.kbase.ai.entity.AiMessageSourceId;

import org.springframework.data.jpa.repository.JpaRepository;

/** Historical citation snapshot persistence. */
public interface AiMessageSourceRepository extends JpaRepository<AiMessageSource, AiMessageSourceId> {

    List<AiMessageSource> findAllByIdAssistantMessageIdOrderByIdSourceOrder(UUID assistantMessageId);
}
