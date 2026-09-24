package com.kbase.ai.retrieval;

import java.util.ArrayList;
import java.util.List;

import com.kbase.ai.provider.model.AiChatMessage;

import org.springframework.stereotype.Component;

/** Retains recent whole turns; conversation text never becomes evidence. */
@Component
public class ConversationContextPolicy {
    static final int MAX_TURNS = 8;
    static final int MAX_CHARS = 4_000;

    public List<AiChatMessage> retain(List<AiChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<AiChatMessage> retained = new ArrayList<>();
        int chars = 0;
        for (int i = history.size() - 1; i >= 0 && retained.size() < MAX_TURNS; i--) {
            AiChatMessage turn = history.get(i);
            if (turn == null || turn.content().length() > MAX_CHARS) {
                continue;
            }
            if (chars + turn.content().length() > MAX_CHARS) {
                break;
            }
            retained.add(0, turn);
            chars += turn.content().length();
        }
        return List.copyOf(retained);
    }

    public String retrievalQuery(String question, List<AiChatMessage> retained) {
        if (retained.isEmpty()) {
            return question;
        }
        StringBuilder query = new StringBuilder("RECENT CONTEXT (non-authoritative):\n");
        for (AiChatMessage turn : retained) {
            query.append(turn.role()).append(": ").append(turn.content()).append('\n');
        }
        return query.append("CURRENT QUESTION:\n").append(question).toString();
    }
}
