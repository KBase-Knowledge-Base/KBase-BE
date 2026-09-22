package com.kbase.ai.job;

import com.kbase.ai.enums.AiJobType;

/** Feature-local handler boundary. M3 deliberately has no production handler implementations. */
public interface AiJobHandler {

    AiJobType jobType();

    AiJobExecutionResult handle(AiJobClaim claim);
}
