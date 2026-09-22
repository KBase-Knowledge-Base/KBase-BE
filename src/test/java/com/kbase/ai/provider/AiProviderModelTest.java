package com.kbase.ai.provider;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.kbase.ai.provider.model.AiChatMessage;
import com.kbase.ai.provider.model.AiChatRequest;
import com.kbase.ai.provider.model.AiEvidenceBlock;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.model.AiChatRole;
import com.kbase.ai.provider.model.EmbeddingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderModelTest {

    @Test
    void chatRequestCopiesConversationAndEvidenceLists() {
        List<AiChatMessage> conversation = List.of(AiChatMessage.user("previous question"));
        List<AiEvidenceBlock> evidence = List.of(new AiEvidenceBlock("source-1", "bounded evidence"));

        AiChatRequest request = new AiChatRequest("ground the answer", conversation, evidence, "current question");

        assertThat(request.conversation()).containsExactly(AiChatMessage.user("previous question"));
        assertThat(request.evidence()).containsExactly(new AiEvidenceBlock("source-1", "bounded evidence"));
        assertThat(request.conversation()).isUnmodifiable();
        assertThat(request.evidence()).isUnmodifiable();
    }

    @Test
    void embeddingRequestPreservesQueryAndDocumentIntentWithoutVendorTypes() {
        AiEmbeddingRequest query = AiEmbeddingRequest.query("what is KBase?");
        AiEmbeddingRequest document = AiEmbeddingRequest.document("guide", "KBase is a knowledge base.");

        assertThat(query.mode()).isEqualTo(EmbeddingMode.QUERY);
        assertThat(query.inputType()).isEqualTo(EmbeddingMode.QUERY);
        assertThat(document.mode()).isEqualTo(EmbeddingMode.DOCUMENT);
        assertThat(document.title()).isEqualTo("guide");
        assertThatThrownBy(() -> new AiEmbeddingRequest(EmbeddingMode.QUERY, "question", "title"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void embeddingResultIsImmutableAndReportsDimensions() {
        AiEmbeddingResult result = new AiEmbeddingResult(List.of(0.5, 0.5), "fixture");

        assertThat(result.dimensions()).isEqualTo(2);
        assertThat(result.vector()).containsExactly(0.5, 0.5);
        assertThat(result.vector()).isUnmodifiable();
        assertThat(result.toArray()).containsExactly(0.5, 0.5);
    }

    @Test
    void chatRoleExposesOnlyApplicationRoles() {
        assertThat(AiChatRole.values()).containsExactly(AiChatRole.USER, AiChatRole.ASSISTANT);
    }
}
