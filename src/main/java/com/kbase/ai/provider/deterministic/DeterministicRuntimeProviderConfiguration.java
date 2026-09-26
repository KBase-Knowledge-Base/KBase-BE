package com.kbase.ai.provider.deterministic;

import java.util.Arrays;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.config.AiProperties.DeterministicFailureMode;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.model.AiChatRequest;
import com.kbase.ai.provider.model.AiChatResult;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.port.AiChatModel;
import com.kbase.ai.provider.port.AiEmbeddingModel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Deterministic, network-free provider used only by the Docker runtime gate.
 *
 * <p>This is intentionally packaged so the tested Docker artifact contains
 * the same application code as production. It cannot activate accidentally:
 * it requires AI enabled, explicit {@code deterministic} mode, the
 * {@code runtime-test} profile, and an acknowledgement flag. Gemini remains
 * the default and only production mode.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "kbase.ai", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "kbase.ai.provider", name = "mode", havingValue = "deterministic")
public class DeterministicRuntimeProviderConfiguration implements EnvironmentAware {

    private volatile Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Bean
    AiChatModel deterministicRuntimeChatModel(AiProperties properties) {
        verifyRuntimeTestOnly(properties);
        return request -> {
            failWhenConfigured(properties);
            AiChatRequest checked = require(request);
            if (checked.evidence().isEmpty()) {
                throw new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
            }
            // The RAG layer validates labels; this deliberately cites only
            // evidence that KBase supplied to the provider boundary.
            return new AiChatResult("Deterministic runtime response [SOURCE_1]", "deterministic-runtime");
        };
    }

    @Bean
    AiEmbeddingModel deterministicRuntimeEmbeddingModel(AiProperties properties) {
        verifyRuntimeTestOnly(properties);
        return request -> {
            failWhenConfigured(properties);
            return new AiEmbeddingResult(vectorFor(require(request).content()), "deterministic-runtime");
        };
    }

    private void verifyRuntimeTestOnly(AiProperties properties) {
        boolean runtimeTestProfile = Arrays.asList(environment.getActiveProfiles()).contains("runtime-test");
        boolean acknowledged = properties.getProvider().getDeterministic().isRuntimeTestAcknowledged();
        if (!runtimeTestProfile || !acknowledged) {
            throw new IllegalStateException(
                    "Deterministic AI provider requires the runtime-test profile and explicit acknowledgement.");
        }
    }

    private static void failWhenConfigured(AiProperties properties) {
        DeterministicFailureMode mode = properties.getProvider().getDeterministic().getFailureMode();
        if (mode == DeterministicFailureMode.UNAVAILABLE) {
            throw new AiProviderException(AiProviderErrorCategory.UNAVAILABLE);
        }
        if (mode == DeterministicFailureMode.TIMEOUT) {
            throw new AiProviderException(AiProviderErrorCategory.TIMEOUT);
        }
    }

    private static <T> T require(T value) {
        if (value == null) {
            throw new AiProviderException(AiProviderErrorCategory.INVALID_RESPONSE);
        }
        return value;
    }

    /**
     * Stable content-only vectors make exact document phrases reproducibly
     * retrievable. The small, explicit vocabulary additionally lets the
     * runtime Guide smoke ask a normal documented permission question rather
     * than feeding an internal chunk back as its query. Unknown content stays
     * SHA-256-derived, so it cannot accidentally become evidence by sharing a
     * generic fallback vector. No credential, clock, I/O or provider data is
     * involved.
     */
    private static java.util.List<Double> vectorFor(String content) {
        String normalized = content.trim().toLowerCase(java.util.Locale.ROOT);
        int[] axes = documentedConceptAxes(normalized);
        double[] values = new double[768];
        if (axes.length > 0) {
            for (int axis : axes) values[axis] = 1.0;
            return normalized(values);
        }

        byte[] bytes = normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            for (int index = 0; index < values.length; index++) {
                digest.update(bytes);
                digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(index).array());
                values[index] = java.nio.ByteBuffer.wrap(digest.digest()).getLong() / (double) Long.MAX_VALUE;
            }
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
        return normalized(values);
    }

    private static int[] documentedConceptAxes(String content) {
        java.util.List<Integer> axes = new java.util.ArrayList<>(6);
        if (containsAny(content, "member", "thành viên")) axes.add(0);
        if (containsAny(content, "owner", "chủ sở hữu")) axes.add(1);
        if (containsAny(content, "document", "file", "tài liệu", "tệp")) axes.add(2);
        if (containsAny(content, "delete", "xóa")) axes.add(3);
        if (containsAny(content, "project", "dự án")) axes.add(4);
        if (containsAny(content, "invitation", "invite", "lời mời")) axes.add(5);
        return axes.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean containsAny(String content, String... candidates) {
        for (String candidate : candidates) {
            if (content.contains(candidate)) return true;
        }
        return false;
    }

    private static java.util.List<Double> normalized(double[] values) {
        double norm = 0.0;
        for (double value : values) norm += value * value;
        norm = Math.sqrt(norm);
        java.util.List<Double> normalized = new java.util.ArrayList<>(values.length);
        for (double value : values) normalized.add(value / norm);
        return java.util.List.copyOf(normalized);
    }
}
