package com.kbase.ai.job;

import java.time.Clock;
import java.util.Objects;

import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.repository.AiConversationRepository;
import com.kbase.ai.repository.AiJobClaimRepository;
import com.kbase.ai.service.AiConversationRetentionService;
import com.kbase.project.repository.ProjectMemberRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provider-independent destructive retention worker. It never reads the job
 * payload: the claimed row identity and its current lease are authoritative.
 */
@Component
public class ConversationPurgeJobHandler implements AiJobHandler {

    public static final String FAILURE_MALFORMED_CLAIM = "INVALID_PURGE_CLAIM";

    private final AiJobClaimRepository jobClaims;
    private final ProjectMemberRepository members;
    private final AiConversationRepository conversations;
    private final Clock clock;

    public ConversationPurgeJobHandler(AiJobClaimRepository jobClaims,
            ProjectMemberRepository members, AiConversationRepository conversations,
            @Qualifier("aiClock") Clock clock) {
        this.jobClaims = Objects.requireNonNull(jobClaims, "jobClaims");
        this.members = Objects.requireNonNull(members, "members");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AiJobType jobType() {
        return AiJobType.CONVERSATION_PURGE;
    }

    @Override
    @Transactional
    public AiJobExecutionResult handle(AiJobClaim claim) {
        if (!isValid(claim)) {
            return AiJobExecutionResult.failure(FAILURE_MALFORMED_CLAIM);
        }

        // The advisory lock linearizes membership-loss enqueue, invitation
        // rejoin/cancellation, and this check/delete sequence across nodes.
        jobClaims.lockDedupKey(AiConversationRetentionService.purgeDedupKey(
                claim.projectId(), claim.userId()));
        if (!jobClaims.hasCurrentConversationPurgeLease(claim.id(), claim.projectId(),
                claim.userId(), claim.leaseToken(), clock.instant())) {
            return AiJobExecutionResult.success();
        }
        if (members.existsByProjectIdAndUserId(claim.projectId(), claim.userId())) {
            return AiJobExecutionResult.success();
        }

        // A zero-row delete is intentionally successful: manual deletion and
        // stale/duplicate delivery must be idempotent.
        conversations.deleteByProjectIdAndCreatedByUserId(claim.projectId(), claim.userId());
        conversations.flush();
        return AiJobExecutionResult.success();
    }

    private static boolean isValid(AiJobClaim claim) {
        return claim != null
                && claim.id() != null
                && claim.jobType() == AiJobType.CONVERSATION_PURGE
                && claim.projectId() != null
                && claim.userId() != null
                && claim.documentId() == null
                && claim.leaseToken() != null
                && !claim.leaseToken().isBlank();
    }
}
