package com.kbase.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectAssistantTitleTest {

    @Test
    void initialTitleStripsEdgesAndTruncatesWithoutSplittingUnicodeCodePoints() {
        String question = "  " + "😀".repeat(100) + "tail  ";
        String title = ProjectAssistantConversationService.initialTitle(question);
        assertThat(title).isEqualTo("😀".repeat(100));
        assertThat(title.codePointCount(0, title.length())).isEqualTo(100);
        assertThat(ProjectAssistantConversationService.initialTitle("  first  second  "))
                .isEqualTo("first  second");
        assertThat(ProjectAssistantConversationService.initialTitle("\u00a0question\u00a0"))
                .isEqualTo("question");
    }
}
