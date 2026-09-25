package com.kbase.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.enums.AiAnswerType;
import com.kbase.ai.guide.GuideSourceCatalog;
import com.kbase.ai.provider.fake.FakeAiChatModel;
import com.kbase.ai.provider.fake.FakeAiEmbeddingModel;
import com.kbase.ai.repository.AiGuideSourceRepository;
import com.kbase.ai.repository.AiVectorRepository;
import com.kbase.ai.repository.GuideChunkMatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GuideRagServiceTest {
    private AiVectorRepository vectors;
    private FakeAiChatModel chat;
    private AiProperties properties;
    private GuideRagService service;

    @BeforeEach
    void setUp() {
        vectors = mock(AiVectorRepository.class);
        chat = new FakeAiChatModel("Grounded answer [SOURCE_1]");
        properties = new AiProperties();
        properties.setRetrievalSimilarityThreshold(null);
        service = new GuideRagService(new FakeAiEmbeddingModel(), chat, vectors,
                mock(AiGuideSourceRepository.class), new GuideSourceCatalog(),
                new ConversationContextPolicy(), properties);
    }

    @Test
    void nullThresholdFailsClosedWithoutCallingChat() {
        when(vectors.findNearestGuideChunks(any(), eq(10))).thenReturn(List.of(chunk(1.0)));

        var result = service.answer("How do project roles work?", List.of());

        assertThat(result.answerType()).isEqualTo(AiAnswerType.NO_EVIDENCE);
        assertThat(result.answer()).isEqualTo(GuideRagService.NO_EVIDENCE_TEXT);
        assertThat(result.sources()).isEmpty();
        assertThat(chat.invocationCount()).isZero();
    }

    private GuideChunkMatch chunk(double similarity) {
        return new GuideChunkMatch(UUID.randomUUID(), UUID.randomUUID(), 1, 0,
                "Approved product evidence", "KBase > Permissions", 3,
                "a".repeat(64), similarity);
    }
}
