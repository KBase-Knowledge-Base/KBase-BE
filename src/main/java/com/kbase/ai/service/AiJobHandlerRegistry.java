package com.kbase.ai.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.job.AiJobHandler;

import org.springframework.stereotype.Component;

/** Production registry: empty in M3, so later job types cannot be consumed accidentally. */
@Component
public class AiJobHandlerRegistry {

    private final Map<AiJobType, AiJobHandler> handlers;

    public AiJobHandlerRegistry(List<AiJobHandler> handlers) {
        EnumMap<AiJobType, AiJobHandler> registered = new EnumMap<>(AiJobType.class);
        for (AiJobHandler handler : handlers) {
            AiJobHandler previous = registered.put(handler.jobType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Multiple AI handlers registered for " + handler.jobType());
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    public Set<AiJobType> supportedJobTypes() {
        return handlers.keySet();
    }

    public Optional<AiJobHandler> handlerFor(AiJobType jobType) {
        return Optional.ofNullable(handlers.get(jobType));
    }
}
