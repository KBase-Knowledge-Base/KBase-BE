package com.kbase.ai.guide;

import java.time.Clock;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kbase.ai.entity.AiGuideSource;
import com.kbase.ai.enums.AiGuideSourceStatus;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.job.AiJobSchedule;
import com.kbase.ai.repository.AiGuideSourceRepository;
import com.kbase.ai.service.AiJobStore;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reconciles the immutable packaged corpus with durable source/index intent. */
@Service
@ConditionalOnProperty(prefix = "kbase.ai", name = "enabled", havingValue = "true")
public class GuideSourceSynchronizationService {
    private final GuideSourceLoader loader;
    private final AiGuideSourceRepository sources;
    private final AiJobStore jobs;
    private final Clock clock;

    public GuideSourceSynchronizationService(GuideSourceLoader loader, AiGuideSourceRepository sources,
            AiJobStore jobs, @Qualifier("aiClock") Clock clock) {
        this.loader = loader;
        this.sources = sources;
        this.jobs = jobs;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeOnStartup() {
        synchronize();
    }

    @Transactional
    public void synchronize() {
        for (LoadedGuideSource loaded : loader.loadAll()) {
            AiGuideSource source = sources.findBySourceKey(loaded.definition().sourceKey()).orElse(null);
            boolean enqueue = false;
            if (source == null) {
                source = new AiGuideSource(loaded.definition().sourceKey(), loaded.contentHash(), 1,
                        AiGuideSourceStatus.PENDING);
                source = sources.save(source);
                enqueue = true;
            } else if (!Objects.equals(source.getContentHash(), loaded.contentHash())) {
                source.setContentHash(loaded.contentHash());
                source.setDesiredVersion(source.getDesiredVersion() + 1);
                if (source.getActiveVersion() == null) {
                    source.setStatus(AiGuideSourceStatus.PENDING);
                }
                // Last-good active chunks remain READY while replacement runs.
                sources.save(source);
                enqueue = true;
            }
            if (enqueue) {
                jobs.enqueueActive(new AiJobSchedule(AiJobType.GUIDE_REINDEX, null, null, null,
                        "guide-reindex:" + source.getId() + ":" + source.getDesiredVersion(),
                        payload(source.getId(), source.getDesiredVersion()), clock.instant(), 3));
            }
        }
    }

    private String payload(java.util.UUID sourceId, long version) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    new GuideReindexPayload(GuideReindexPayload.SCHEMA_VERSION, sourceId, version));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Guide job payload could not be encoded", exception);
        }
    }
}
