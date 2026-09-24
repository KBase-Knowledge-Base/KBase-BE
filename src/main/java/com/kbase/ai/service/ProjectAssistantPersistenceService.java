package com.kbase.ai.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.kbase.ai.dto.response.AiConversationResponse;
import com.kbase.ai.dto.response.AiMessageResponse;
import com.kbase.ai.dto.response.AiSourceResponse;
import com.kbase.ai.dto.response.AiTurnResponse;
import com.kbase.ai.entity.AiConversation;
import com.kbase.ai.entity.AiMessage;
import com.kbase.ai.entity.AiMessageSource;
import com.kbase.ai.enums.AiAnswerType;
import com.kbase.ai.enums.AiGenerationStatus;
import com.kbase.ai.enums.AiMessageRole;
import com.kbase.ai.provider.model.AiChatMessage;
import com.kbase.ai.repository.AiConversationQuotaRepository;
import com.kbase.ai.repository.AiConversationRepository;
import com.kbase.ai.repository.AiMessageRepository;
import com.kbase.ai.repository.AiMessageSourceRepository;
import com.kbase.ai.retrieval.CitationSnapshotMapper;
import com.kbase.ai.retrieval.GroundedResult;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.user.entity.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short database transactions around conversation state; no provider call lives here. */
@Service
public class ProjectAssistantPersistenceService {

    private static final Sort CONVERSATION_ORDER = Sort.by(Sort.Order.desc("updatedAt"),
            Sort.Order.desc("id"));
    private static final Sort MESSAGE_ORDER = Sort.by(Sort.Order.asc("createdAt"),
            Sort.Order.asc("id"));

    private final ProjectAuthorizationService authorization;
    private final AiConversationQuotaRepository quotaRepository;
    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final AiMessageSourceRepository sources;
    private final CitationSnapshotMapper citations;

    public ProjectAssistantPersistenceService(ProjectAuthorizationService authorization,
            AiConversationQuotaRepository quotaRepository, AiConversationRepository conversations,
            AiMessageRepository messages, AiMessageSourceRepository sources,
            CitationSnapshotMapper citations) {
        this.authorization = authorization;
        this.quotaRepository = quotaRepository;
        this.conversations = conversations;
        this.messages = messages;
        this.sources = sources;
        this.citations = citations;
    }

    @Transactional
    public StartedTurn create(UUID projectId, CustomUserPrincipal principal,
            String question, String title) {
        var access = authorization.requireProjectAccess(projectId, principal);
        User creator = quotaRepository.lockUserForConversationQuota(principal.getUserId());
        if (creator == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (quotaRepository.countConversations(projectId, principal.getUserId()) >= 5) {
            throw new BusinessException(ErrorCode.AI_CONVERSATION_LIMIT_REACHED);
        }
        AiConversation conversation = conversations.saveAndFlush(
                new AiConversation(access.project(), creator, title));
        AiMessage user = messages.saveAndFlush(new AiMessage(conversation, AiMessageRole.USER,
                question, AiGenerationStatus.COMPLETED));
        AiMessage assistant = messages.saveAndFlush(new AiMessage(conversation,
                AiMessageRole.ASSISTANT, null, AiGenerationStatus.PROCESSING));
        return new StartedTurn(conversation.getId(), assistant.getId(), user.getContent(),
                AiConversationResponse.from(conversation), List.of());
    }

    @Transactional
    public StartedTurn startSend(UUID projectId, UUID conversationId,
            CustomUserPrincipal principal, String question) {
        AiConversation conversation = requireOwned(projectId, conversationId, principal);
        if (messages.existsByConversationIdAndRoleAndGenerationStatus(conversationId,
                AiMessageRole.ASSISTANT, AiGenerationStatus.PROCESSING)) {
            throw new BusinessException(ErrorCode.AI_REQUEST_IN_PROGRESS);
        }
        List<AiMessage> recent = messages.findRecentTextContext(conversationId, PageRequest.of(0, 8));
        List<AiChatMessage> history = new ArrayList<>(recent.size());
        for (AiMessage message : recent) {
            history.add(message.getRole() == AiMessageRole.USER
                    ? AiChatMessage.user(message.getContent())
                    : AiChatMessage.assistant(message.getContent()));
        }
        Collections.reverse(history);

        messages.saveAndFlush(new AiMessage(conversation, AiMessageRole.USER,
                question, AiGenerationStatus.COMPLETED));
        AiMessage assistant = messages.saveAndFlush(new AiMessage(conversation,
                AiMessageRole.ASSISTANT, null, AiGenerationStatus.PROCESSING));
        conversation.setUpdatedAt(Instant.now());
        conversations.saveAndFlush(conversation);
        return new StartedTurn(conversationId, assistant.getId(), question,
                AiConversationResponse.from(conversation), List.copyOf(history));
    }

    @Transactional
    public AiTurnResponse complete(UUID projectId, CustomUserPrincipal principal,
            StartedTurn started, GroundedResult result) {
        requireOwned(projectId, started.conversationId(), principal);
        AiMessage assistant = messages.findByIdAndConversationIdAndRoleAndGenerationStatus(
                        started.assistantId(), started.conversationId(), AiMessageRole.ASSISTANT,
                        AiGenerationStatus.PROCESSING)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_CONVERSATION_NOT_FOUND));
        List<AiMessageSource> mapped = result.answerType() == AiAnswerType.GROUNDED
                ? citations.map(assistant.getId(), result.sources()) : List.of();
        assistant.setContent(result.text());
        assistant.setAnswerType(result.answerType());
        assistant.setGenerationStatus(AiGenerationStatus.COMPLETED);
        assistant.setModel(result.modelId() == null || result.modelId().isBlank()
                ? null : result.modelId());
        assistant.setFailureCode(null);
        assistant.setCompletedAt(Instant.now());
        messages.saveAndFlush(assistant);
        if (!mapped.isEmpty()) {
            sources.saveAllAndFlush(mapped);
        }
        return new AiTurnResponse(AiMessageResponse.from(assistant),
                mapped.stream().map(AiSourceResponse::from).toList());
    }

    /** Internal cleanup after a failed request; it never grants data access to the caller. */
    @Transactional
    public void markFailed(UUID projectId, UUID creatorId, StartedTurn started, String failureCode) {
        if (conversations.findByIdAndProjectIdAndCreatedByUserId(
                started.conversationId(), projectId, creatorId).isEmpty()) {
            return;
        }
        messages.findByIdAndConversationIdAndRoleAndGenerationStatus(
                        started.assistantId(), started.conversationId(), AiMessageRole.ASSISTANT,
                        AiGenerationStatus.PROCESSING)
                .ifPresent(assistant -> {
                    assistant.setContent(null);
                    assistant.setAnswerType(null);
                    assistant.setModel(null);
                    assistant.setGenerationStatus(AiGenerationStatus.FAILED);
                    assistant.setFailureCode(failureCode);
                    assistant.setCompletedAt(Instant.now());
                    messages.saveAndFlush(assistant);
                });
    }

    @Transactional(readOnly = true)
    public PageResponse<AiConversationResponse> list(UUID projectId, CustomUserPrincipal principal,
            Pageable pageable) {
        authorization.requireProjectAccess(projectId, principal);
        Page<AiConversation> page = conversations.findByProjectIdAndCreatedByUserId(projectId,
                principal.getUserId(), PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        CONVERSATION_ORDER));
        return PageResponse.from(page, AiConversationResponse::from);
    }

    @Transactional(readOnly = true)
    public AiConversationResponse get(UUID projectId, UUID conversationId,
            CustomUserPrincipal principal) {
        return AiConversationResponse.from(requireOwned(projectId, conversationId, principal));
    }

    @Transactional
    public AiConversationResponse rename(UUID projectId, UUID conversationId,
            CustomUserPrincipal principal, String title) {
        AiConversation conversation = requireOwned(projectId, conversationId, principal);
        conversation.setTitle(title);
        conversation.setUpdatedAt(Instant.now());
        return AiConversationResponse.from(conversations.saveAndFlush(conversation));
    }

    @Transactional
    public void delete(UUID projectId, UUID conversationId, CustomUserPrincipal principal) {
        AiConversation conversation = requireOwned(projectId, conversationId, principal);
        conversations.delete(conversation);
        conversations.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<AiTurnResponse> listMessages(UUID projectId, UUID conversationId,
            CustomUserPrincipal principal, Pageable pageable) {
        requireOwned(projectId, conversationId, principal);
        Page<AiMessage> page = messages.findByConversationId(conversationId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), MESSAGE_ORDER));
        List<UUID> assistantIds = page.getContent().stream()
                .filter(message -> message.getRole() == AiMessageRole.ASSISTANT)
                .map(AiMessage::getId).toList();
        Map<UUID, List<AiSourceResponse>> grouped = assistantIds.isEmpty() ? Map.of()
                : sources.findAllByIdAssistantMessageIdInOrderByIdAssistantMessageIdAscIdSourceOrderAsc(
                                assistantIds).stream()
                        .collect(Collectors.groupingBy(AiMessageSource::getAssistantMessageId,
                                Collectors.mapping(AiSourceResponse::from, Collectors.toList())));
        return PageResponse.from(page, message -> new AiTurnResponse(AiMessageResponse.from(message),
                grouped.getOrDefault(message.getId(), List.of())));
    }

    private AiConversation requireOwned(UUID projectId, UUID conversationId,
            CustomUserPrincipal principal) {
        authorization.requireProjectAccess(projectId, principal);
        return conversations.findByIdAndProjectIdAndCreatedByUserId(
                        conversationId, projectId, principal.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_CONVERSATION_NOT_FOUND));
    }

    public record StartedTurn(UUID conversationId, UUID assistantId, String question,
            AiConversationResponse conversation, List<AiChatMessage> history) {
        public StartedTurn {
            history = List.copyOf(history);
        }
    }
}
