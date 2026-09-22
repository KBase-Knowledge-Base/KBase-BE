package com.kbase.ai.provider.springai;

import java.time.Duration;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.kbase.ai.config.AiProperties;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.port.AiChatModel;
import com.kbase.ai.provider.port.AiEmbeddingModel;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import io.micrometer.observation.ObservationRegistry;

/**
 * Explicit, disabled-by-default Gemini/Spring AI wiring owned by KBase.
 *
 * <p>Vendor auto-configuration remains excluded in application.yml. This
 * boundary validates the credential only when AI is enabled and exposes only
 * KBase ports to application code.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "kbase.ai", name = "enabled", havingValue = "true")
public class AiGeminiProviderConfiguration {

    @Bean(name = "aiGeminiChatClient", destroyMethod = "close")
    Client aiGeminiChatClient(AiProperties properties) {
        return createClient(properties);
    }

    @Bean(name = "aiGeminiEmbeddingClient", destroyMethod = "close")
    Client aiGeminiEmbeddingClient(AiProperties properties) {
        return createClient(properties);
    }

    @Bean
    GoogleGenAiChatModel aiGeminiChatModel(
            @Qualifier("aiGeminiChatClient") Client client, AiProperties properties) {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(properties.getGemini().getChatModel())
                .build();
        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(options)
                .toolCallingManager(ToolCallingManager.builder().build())
                .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    @Bean
    GoogleGenAiTextEmbeddingModel aiGeminiTextEmbeddingModel(
            @Qualifier("aiGeminiEmbeddingClient") Client client, AiProperties properties) {
        GoogleGenAiEmbeddingConnectionDetails connectionDetails = GoogleGenAiEmbeddingConnectionDetails.builder()
                .apiKey(requireApiKey(properties))
                .genAiClient(client)
                .build();
        GoogleGenAiTextEmbeddingOptions options = GoogleGenAiTextEmbeddingOptions.builder()
                .model(properties.getGemini().getEmbeddingModel())
                .dimensions(properties.getGemini().getEmbeddingDimensions())
                .build();
        return new GoogleGenAiTextEmbeddingModel(connectionDetails, options);
    }

    @Bean
    @Primary
    AiChatModel aiChatModel(ChatModel chatModel, AiProperties properties) {
        return new SpringAiGeminiChatAdapter(chatModel, properties.getGemini().getChatModel());
    }

    @Bean
    @Primary
    AiEmbeddingModel aiEmbeddingModel(EmbeddingModel embeddingModel, AiProperties properties) {
        return new SpringAiGeminiEmbeddingAdapter(embeddingModel, properties);
    }

    private static Client createClient(AiProperties properties) {
        String apiKey = requireApiKey(properties);
        Duration requestTimeout = properties.getProvider().getRequestTimeout();
        long timeoutMillis = requestTimeout.toMillis();
        if (timeoutMillis <= 0 || timeoutMillis > Integer.MAX_VALUE) {
            throw new AiProviderException(AiProviderErrorCategory.CONFIGURATION);
        }

        HttpOptions httpOptions = HttpOptions.builder()
                .timeout((int) timeoutMillis)
                .build();
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(httpOptions)
                .build();
    }

    private static String requireApiKey(AiProperties properties) {
        if (properties == null || properties.getGemini() == null
                || properties.getGemini().getApiKey() == null
                || properties.getGemini().getApiKey().isBlank()) {
            throw new AiProviderException(AiProviderErrorCategory.CONFIGURATION);
        }
        return properties.getGemini().getApiKey();
    }
}
