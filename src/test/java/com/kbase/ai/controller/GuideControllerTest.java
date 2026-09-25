package com.kbase.ai.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.kbase.ai.dto.request.GuideQueryRequest;
import com.kbase.ai.observability.AiObservability;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.retrieval.GuideRagService;
import com.kbase.ai.usage.AiUsageGuard;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class GuideControllerTest {

    @Test
    void mapsProviderFailureToStable503Contract() {
        @SuppressWarnings("unchecked")
        ObjectProvider<GuideRagService> guide = mock(ObjectProvider.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        AiUsageGuard usageGuard = mock(AiUsageGuard.class);
        GuideRagService service = mock(GuideRagService.class);
        CustomUserPrincipal principal = new CustomUserPrincipal(UUID.randomUUID(), "user@example.test",
                SystemRole.USER, UserStatus.ACTIVE, true);
        when(currentUser.requirePrincipal()).thenReturn(principal);
        when(guide.getIfAvailable()).thenReturn(service);
        doThrow(new AiProviderException(AiProviderErrorCategory.UNAVAILABLE))
                .when(service).answer("question", java.util.List.of());

        GuideController controller = new GuideController(guide, currentUser, usageGuard,
                new AiObservability());

        assertThatThrownBy(() -> controller.query(new GuideQueryRequest("question", java.util.List.of())))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.getErrorCode())
                                .isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE));
    }
}
