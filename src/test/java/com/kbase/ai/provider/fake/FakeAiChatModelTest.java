package com.kbase.ai.provider.fake;

import org.junit.jupiter.api.Test;

import com.kbase.ai.provider.model.AiChatRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeAiChatModelTest {

    @Test
    void defaultResponseIsDeterministicAndRequestsAreCaptured() {
        FakeAiChatModel fake = new FakeAiChatModel();
        AiChatRequest request = AiChatRequest.of("same question");

        assertThat(fake.generate(request)).isEqualTo(fake.generate(request));
        assertThat(fake.invocationCount()).isEqualTo(2);
        assertThat(fake.capturedRequests()).containsExactly(request, request);
        assertThat(fake.capturedRequests()).isUnmodifiable();
    }

    @Test
    void fixtureAndFailureControlsAreAvailableWithoutNetwork() {
        FakeAiChatModel fake = new FakeAiChatModel().withResponse(new com.kbase.ai.provider.model.AiChatResult("fixture"));
        assertThat(fake.generate(AiChatRequest.of("question")).generatedText()).isEqualTo("fixture");

        fake.simulateUnavailable();
        assertThatThrownBy(() -> fake.generate(AiChatRequest.of("question")))
                .isInstanceOf(FakeAiProviderException.class)
                .extracting(exception -> ((FakeAiProviderException) exception).getKind())
                .isEqualTo(FakeAiProviderException.Kind.UNAVAILABLE);

        fake.clearFailure().simulateTimeout();
        assertThatThrownBy(() -> fake.generate(AiChatRequest.of("question")))
                .isInstanceOf(FakeAiProviderException.class)
                .extracting(exception -> ((FakeAiProviderException) exception).getKind())
                .isEqualTo(FakeAiProviderException.Kind.TIMEOUT);
    }

    @Test
    void invocationCountCanProveAProviderWasNotCalled() {
        FakeAiChatModel fake = new FakeAiChatModel();

        assertThat(fake.invocationCount()).isZero();
    }
}
