package com.kbase.ai.service;

import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.dto.response.AiConversationResponse;
import com.kbase.ai.dto.response.AiTurnResponse;
import com.kbase.ai.dto.response.CreateAiConversationResponse;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.retrieval.GroundedResult;
import com.kbase.ai.retrieval.ProjectRagService;
import com.kbase.ai.service.ProjectAssistantPersistenceService.StartedTurn;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.exception.KBaseException;
import com.kbase.shared.pagination.PageResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Nontransactional orchestration of a persisted turn around the strict M6 RAG boundary. */
@Service
public class ProjectAssistantConversationService {

    private final ProjectAssistantPersistenceService persistence;
    private final ObjectProvider<ProjectRagService> ragProvider;
    private final ProjectAuthorizationService authorization;
    private final ProjectAssistantFinalizationHook finalizationHook;
    private final AiProperties properties;

    public ProjectAssistantConversationService(ProjectAssistantPersistenceService persistence,
            ObjectProvider<ProjectRagService> ragProvider,
            ProjectAuthorizationService authorization,
            ProjectAssistantFinalizationHook finalizationHook, AiProperties properties) {
        this.persistence = persistence;
        this.ragProvider = ragProvider;
        this.authorization = authorization;
        this.finalizationHook = finalizationHook;
        this.properties = properties;
    }

    public CreateAiConversationResponse create(UUID projectId, CustomUserPrincipal principal,
            String message) {
        String question = validMessage(message);
        ProjectRagService rag = requireRag();
        StartedTurn started = persistence.create(projectId, principal, question, initialTitle(question));
        AiTurnResponse turn = answer(projectId, principal, started, rag);
        return new CreateAiConversationResponse(started.conversation(), turn.message(), turn.sources());
    }

    public AiTurnResponse send(UUID projectId, UUID conversationId,
            CustomUserPrincipal principal, String message) {
        String question = validMessage(message);
        ProjectRagService rag = requireRag();
        StartedTurn started = persistence.startSend(projectId, conversationId, principal, question);
        return answer(projectId, principal, started, rag);
    }

    public PageResponse<AiConversationResponse> list(UUID projectId, CustomUserPrincipal principal,
            Pageable pageable) {
        return persistence.list(projectId, principal, pageable);
    }

    public AiConversationResponse get(UUID projectId, UUID conversationId,
            CustomUserPrincipal principal) {
        return persistence.get(projectId, conversationId, principal);
    }

    public AiConversationResponse rename(UUID projectId, UUID conversationId,
            CustomUserPrincipal principal, String title) {
        if (title == null || stripEdges(title).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String trimmed = stripEdges(title);
        if (trimmed.codePointCount(0, trimmed.length()) > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return persistence.rename(projectId, conversationId, principal, trimmed);
    }

    public void delete(UUID projectId, UUID conversationId, CustomUserPrincipal principal) {
        persistence.delete(projectId, conversationId, principal);
    }

    public PageResponse<AiTurnResponse> listMessages(UUID projectId, UUID conversationId,
            CustomUserPrincipal principal, Pageable pageable) {
        return persistence.listMessages(projectId, conversationId, principal, pageable);
    }

    static String initialTitle(String firstQuestion) {
        String trimmed = stripEdges(firstQuestion);
        int codePoints = trimmed.codePointCount(0, trimmed.length());
        return codePoints <= 100 ? trimmed : trimmed.substring(0, trimmed.offsetByCodePoints(0, 100));
    }

    private String validMessage(String message) {
        if (message == null || stripEdges(message).isEmpty()
                || message.codePointCount(0, message.length()) > properties.getMaxMessageChars()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return message;
    }

    private static String stripEdges(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private ProjectRagService requireRag() {
        ProjectRagService rag = properties.isEnabled() ? ragProvider.getIfAvailable() : null;
        if (rag == null) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        }
        return rag;
    }

    private AiTurnResponse answer(UUID projectId, CustomUserPrincipal principal,
            StartedTurn started, ProjectRagService rag) {
        GroundedResult result;
        try {
            result = rag.answer(projectId, principal, started.question(), started.history());
        } catch (RuntimeException failure) {
            throw failTurn(projectId, principal, started, failure);
        }
        try {
            finalizationHook.beforeFinalization(projectId, started.conversationId());
            AiTurnResponse response = persistence.complete(projectId, principal, started, result);
            authorization.requireProjectAccess(projectId, principal);
            return response;
        } catch (RuntimeException failure) {
            throw failTurn(projectId, principal, started, failure);
        }
    }

    private RuntimeException failTurn(UUID projectId, CustomUserPrincipal principal,
            StartedTurn started, RuntimeException failure) {
        KBaseException accessFailure = null;
        try {
            authorization.requireProjectAccess(projectId, principal);
        } catch (KBaseException revoked) {
            accessFailure = revoked;
        }
        String safeCode = accessFailure != null ? "PROJECT_ACCESS_REVOKED"
                : failure instanceof AiProviderException provider
                        ? "AI_PROVIDER_" + provider.category().name() : "AI_PROVIDER_UNAVAILABLE";
        persistence.markFailed(projectId, principal.getUserId(), started, safeCode);
        if (accessFailure != null) {
            return accessFailure;
        }
        if (failure instanceof KBaseException known
                && known.getErrorCode() == ErrorCode.AI_CONVERSATION_NOT_FOUND) {
            return known;
        }
        return new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
    }
}
