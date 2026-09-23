package com.kbase.ai.job;

import com.kbase.ai.enums.AiJobType;

/** Feature-local handler boundary for durable AI job execution. */
public interface AiJobHandler {

    AiJobType jobType();

    AiJobExecutionResult handle(AiJobClaim claim);
}
