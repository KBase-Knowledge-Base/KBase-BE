package com.kbase.ai.retrieval;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.enums.AiAnswerType;
import com.kbase.ai.provider.model.AiChatMessage;
import com.kbase.ai.provider.model.AiChatResult;
import com.kbase.ai.provider.port.AiChatModel;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Internal strict RAG pipeline. Provider work is outside a database transaction. */
@Service
@ConditionalOnProperty(prefix = "kbase.ai", name = "enabled", havingValue = "true")
public class ProjectRagService {
    public static final String NO_EVIDENCE_TEXT =
            "Tôi chưa tìm thấy đủ thông tin trong tài liệu hiện có của project để trả lời câu hỏi này.";

    private final ProjectEvidenceRetriever retriever;
    private final ConversationContextPolicy contextPolicy;
    private final EvidenceSelector selector;
    private final GroundedPromptBuilder promptBuilder;
    private final AiChatModel chatModel;
    private final SourceLabelValidator labels;
    private final ProjectAuthorizationService authorization;
    private final AiProperties properties;

    public ProjectRagService(ProjectEvidenceRetriever retriever,
            ConversationContextPolicy contextPolicy, EvidenceSelector selector,
            GroundedPromptBuilder promptBuilder, AiChatModel chatModel,
            SourceLabelValidator labels, ProjectAuthorizationService authorization,
            AiProperties properties) {
        this.retriever = Objects.requireNonNull(retriever);
        this.contextPolicy = Objects.requireNonNull(contextPolicy);
        this.selector = Objects.requireNonNull(selector);
        this.promptBuilder = Objects.requireNonNull(promptBuilder);
        this.chatModel = Objects.requireNonNull(chatModel);
        this.labels = Objects.requireNonNull(labels);
        this.authorization = Objects.requireNonNull(authorization);
        this.properties = Objects.requireNonNull(properties);
    }

    public GroundedResult answer(UUID projectId, CustomUserPrincipal principal,
            String question, List<AiChatMessage> history) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        List<AiChatMessage> context = contextPolicy.retain(history);
        String query = contextPolicy.retrievalQuery(question, context);
        List<EvidenceBlock> evidence = selector.select(
                retriever.retrieve(projectId, principal, query),
                properties.getRetrievalSimilarityThreshold(),
                properties.getRetrievalFinalContextLimit());
        authorization.requireProjectAccess(projectId, principal);
        if (evidence.isEmpty()) {
            return new GroundedResult(AiAnswerType.NO_EVIDENCE, NO_EVIDENCE_TEXT, "", List.of());
        }
        AiChatResult generated = chatModel.generate(promptBuilder.build(question, context, evidence));
        authorization.requireProjectAccess(projectId, principal);
        List<EvidenceSource> cited = labels.validate(generated == null ? null : generated.text(), evidence);
        return new GroundedResult(AiAnswerType.GROUNDED, generated.text(), generated.modelId(), cited);
    }
}
