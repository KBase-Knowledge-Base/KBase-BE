package com.kbase.ai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.kbase.ai.provider.model.AiChatMessage;

import org.junit.jupiter.api.Test;

class ConversationContextPolicyTest {
    private final ConversationContextPolicy policy = new ConversationContextPolicy();

    @Test
    void keepsEightRecentTurnsInChronologicalOrder() {
        List<AiChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(AiChatMessage.user("turn " + i));
        }
        List<AiChatMessage> retained = policy.retain(history);
        assertThat(retained).hasSize(8);
        assertThat(retained.getFirst().content()).isEqualTo("turn 2");
        assertThat(retained.getLast().content()).isEqualTo("turn 9");
        assertThat(policy.retrievalQuery("question", retained))
                .contains("RECENT CONTEXT", "CURRENT QUESTION:\nquestion")
                .doesNotContain("turn 0");
    }

    @Test
    void characterBudgetRemovesOldestWholeTurn() {
        List<AiChatMessage> retained = policy.retain(List.of(
                AiChatMessage.assistant("x".repeat(3_000)),
                AiChatMessage.user("y".repeat(2_000)),
                AiChatMessage.assistant("recent")));
        assertThat(retained).extracting(AiChatMessage::content)
                .containsExactly("y".repeat(2_000), "recent");
    }
}
