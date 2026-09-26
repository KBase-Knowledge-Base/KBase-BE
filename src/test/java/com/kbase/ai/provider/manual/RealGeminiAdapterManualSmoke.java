package com.kbase.ai.provider.manual;

import java.util.List;

import com.kbase.KBaseApplication;
import com.kbase.ai.provider.model.AiChatRequest;
import com.kbase.ai.provider.model.AiChatResult;
import com.kbase.ai.provider.model.AiEmbeddingRequest;
import com.kbase.ai.provider.model.AiEmbeddingResult;
import com.kbase.ai.provider.model.EmbeddingMode;
import com.kbase.ai.provider.port.AiChatModel;
import com.kbase.ai.provider.port.AiEmbeddingModel;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Manual Real Gemini provider-adapter smoke — never part of the automated
 * gate. The class name does not match Surefire includes and it declares no
 * {@code @Test} methods, so {@code mvn clean verify} cannot run it and the
 * offline suite keeps 0 skipped.
 *
 * <p>Run explicitly (documented local path: deps up via Compose, operator
 * {@code .env} carries the real Gemini config; profile {@code local} imports
 * it — the API key is read from the environment by the application and is
 * never printed by this harness):
 * <pre>
 * mvn -q test-compile \
 *   org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
 *   -Dexec.mainClass=com.kbase.ai.provider.manual.RealGeminiAdapterManualSmoke \
 *   -Dexec.classpathScope=test
 * </pre>
 */
public final class RealGeminiAdapterManualSmoke {

    private RealGeminiAdapterManualSmoke() {
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(KBaseApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("local")
                .properties("server.port=18085")
                .run();
        try {
            var environment = context.getEnvironment();
            String providerMode = environment.getProperty("kbase.ai.provider.mode");
            String chatModel = environment.getProperty("kbase.ai.gemini.chat-model");
            String embeddingModel = environment.getProperty("kbase.ai.gemini.embedding-model");
            String dimensionsProperty = environment.getProperty("kbase.ai.gemini.embedding-dimensions");
            System.out.println("[smoke] provider.mode=" + providerMode
                    + " chatModel=" + chatModel
                    + " embeddingModel=" + embeddingModel
                    + " embeddingDimensions=" + dimensionsProperty);
            boolean fail = false;
            if (!"gemini".equals(providerMode)) {
                System.out.println("[smoke] FAIL: provider mode is not gemini");
                fail = true;
            }
            if (fail) {
                System.exit(1);
            }

            // --- Chat adapter smoke (direct port, no RAG/Project Assistant path) ---
            try {
                AiChatModel chat = context.getBean(AiChatModel.class);
                System.out.println("[smoke] chat adapter bean = " + chat.getClass().getName());
                AiChatResult chatResult = chat.generate(AiChatRequest.of(
                        "Reply exactly with: KBASE_GEMINI_OK"));
                System.out.println("[smoke] CHAT_OK modelId=" + chatResult.modelId()
                        + " responseChars=" + chatResult.text().length()
                        + " response=" + chatResult.text());
            } catch (RuntimeException chatFailure) {
                System.out.println("[smoke] CHAT_FAIL type=" + chatFailure.getClass().getName()
                        + " message=" + chatFailure.getMessage());
                fail = true;
            }

            // --- Embedding adapter smoke (direct port, no indexing/corpus write) ---
            try {
                AiEmbeddingModel embedding = context.getBean(AiEmbeddingModel.class);
                System.out.println("[smoke] embedding adapter bean = " + embedding.getClass().getName());
                AiEmbeddingResult embeddingResult = embedding.embed(new AiEmbeddingRequest(
                        EmbeddingMode.QUERY, "KBase real Gemini embedding smoke test"));
                boolean finite = embeddingResult.vector().stream().allMatch(Double::isFinite);
                System.out.println("[smoke] EMBED_OK modelId=" + embeddingResult.modelId()
                        + " dimensions=" + embeddingResult.dimensions()
                        + " allFinite=" + finite
                        + " expectedDimensions=" + dimensionsProperty
                        + " dimensionsMatch=" + (String.valueOf(embeddingResult.dimensions())
                                .equals(dimensionsProperty)));
                if (embeddingResult.dimensions() != Integer.parseInt(dimensionsProperty) || !finite) {
                    fail = true;
                }
            } catch (RuntimeException embeddingFailure) {
                System.out.println("[smoke] EMBED_FAIL type=" + embeddingFailure.getClass().getName()
                        + " message=" + embeddingFailure.getMessage());
                fail = true;
            }

            System.out.println(fail ? "[smoke] RESULT=FAIL" : "[smoke] RESULT=PASS");
            if (fail) {
                System.exit(1);
            }
        } finally {
            context.close();
        }
    }
}
