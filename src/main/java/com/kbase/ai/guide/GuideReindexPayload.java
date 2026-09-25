package com.kbase.ai.guide;

import java.util.UUID;

/** Deliberately small durable job payload; source text never enters ai_jobs. */
public record GuideReindexPayload(int schemaVersion, UUID guideSourceId, long desiredVersion) {
    public static final int SCHEMA_VERSION = 1;
}
