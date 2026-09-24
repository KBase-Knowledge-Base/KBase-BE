package com.kbase.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.repository.AiConversationRepository;
import com.kbase.ai.repository.AiJobClaimRepository;
import com.kbase.project.repository.ProjectMemberRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationPurgeJobHandlerTest {

    private static final Instant NOW = Instant.parse("2026-01-08T00:00:00Z");

    @Mock
    private AiJobClaimRepository jobs;
    @Mock
    private ProjectMemberRepository members;
    @Mock
    private AiConversationRepository conversations;

    @Test
    void malformedClaimIsPermanentFailureAndCannotDelete() {
        var handler = handler();
        AiJobClaim malformed = new AiJobClaim(UUID.randomUUID(), AiJobType.CONVERSATION_PURGE,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "key", "{}", NOW,
                1, 3, NOW.plusSeconds(30), "lease");

        assertThat(handler.handle(malformed).errorCode())
                .isEqualTo(ConversationPurgeJobHandler.FAILURE_MALFORMED_CLAIM);
        verify(conversations, never()).deleteByProjectIdAndCreatedByUserId(any(), any());
    }

    @Test
    void currentMembershipOrStaleLeaseMakesPurgeHarmless() {
        var handler = handler();
        AiJobClaim claim = claim();
        when(jobs.hasCurrentConversationPurgeLease(eq(claim.id()), eq(claim.projectId()),
                eq(claim.userId()), eq(claim.leaseToken()), eq(NOW))).thenReturn(true);
        when(members.existsByProjectIdAndUserId(claim.projectId(), claim.userId())).thenReturn(true);

        assertThat(handler.handle(claim)).isEqualTo(AiJobExecutionResult.success());
        verify(jobs).lockDedupKey("conversation-purge:" + claim.projectId() + ":" + claim.userId());
        verify(conversations, never()).deleteByProjectIdAndCreatedByUserId(any(), any());
    }

    @Test
    void currentAbsentMembershipDeletesOnlyClaimScope() {
        var handler = handler();
        AiJobClaim claim = claim();
        when(jobs.hasCurrentConversationPurgeLease(eq(claim.id()), eq(claim.projectId()),
                eq(claim.userId()), eq(claim.leaseToken()), eq(NOW))).thenReturn(true);
        when(members.existsByProjectIdAndUserId(claim.projectId(), claim.userId())).thenReturn(false);

        assertThat(handler.handle(claim)).isEqualTo(AiJobExecutionResult.success());
        verify(conversations).deleteByProjectIdAndCreatedByUserId(claim.projectId(), claim.userId());
        verify(conversations).flush();
    }

    private ConversationPurgeJobHandler handler() {
        return new ConversationPurgeJobHandler(jobs, members, conversations,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AiJobClaim claim() {
        return new AiJobClaim(UUID.randomUUID(), AiJobType.CONVERSATION_PURGE,
                UUID.randomUUID(), null, UUID.randomUUID(), "ignored", "{}", NOW,
                1, 3, NOW.plusSeconds(30), "lease");
    }
}
