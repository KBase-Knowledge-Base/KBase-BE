package com.kbase.ai.provider.manual;

import java.util.ArrayList;
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
 * <p>Isolation contract (mechanical, not timing-based): the smoke passes
 * {@code --kbase.ai.worker.scheduling-enabled=false} as a command-line
 * argument — Spring Boot's highest-precedence property source — so operator
 * environment variables cannot override the smoke-only disable. The harness
 * asserts the effective value and the absence of the scheduling
 * infrastructure before any provider call, so {@code AiJobScheduler} cannot
 * claim pending DOCUMENT_INDEX/GUIDE_REINDEX jobs and no background provider
 * traffic exists. Guide startup synchronization only reconciles packaged-
 * corpus hashes and enqueues intent — it never embeds inline. The only
 * provider calls are the two explicit adapter invocations below.
 *
 * <p>Preflight runs BEFORE any provider call: the context must resolve exactly
 * the approved candidate configuration, otherwise the harness exits with
 * RESULT=FAIL without talking to Gemini.
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

    /** The only candidate configuration this smoke is approved to verify. */
    static final String EXPECTED_PROVIDER_MODE = "gemini";
    static final String EXPECTED_CHAT_MODEL = "gemini-3.5-flash-lite";
    static final String EXPECTED_EMBEDDING_MODEL = "gemini-embedding-2";
    static final String EXPECTED_EMBEDDING_DIMENSIONS = "768";

    /** Bean name Spring uses for the scheduling infrastructure. */
    static final String SCHEDULED_ANNOTATION_PROCESSOR_BEAN =
            org.springframework.scheduling.config.TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;

    private RealGeminiAdapterManualSmoke() {
    }

    /**
     * Pure configuration preflight: returns every violation of the approved
     * candidate contract. The smoke must not contact Gemini while this list is
     * non-empty.
     */
    static List<String> candidateViolations(String providerMode, String chatModel,
            String embeddingModel, String embeddingDimensions) {
        List<String> violations = new ArrayList<>();
        if (!EXPECTED_PROVIDER_MODE.equals(providerMode)) {
            violations.add("provider.mode must be " + EXPECTED_PROVIDER_MODE + " but was " + providerMode);
        }
        if (!EXPECTED_CHAT_MODEL.equals(chatModel)) {
            violations.add("chat model must be " + EXPECTED_CHAT_MODEL + " but was " + chatModel);
        }
        if (!EXPECTED_EMBEDDING_MODEL.equals(embeddingModel)) {
            violations.add("embedding model must be " + EXPECTED_EMBEDDING_MODEL
                    + " but was " + embeddingModel);
        }
        if (!EXPECTED_EMBEDDING_DIMENSIONS.equals(embeddingDimensions)) {
            violations.add("embedding dimensions must be " + EXPECTED_EMBEDDING_DIMENSIONS
                    + " but was " + embeddingDimensions);
        }
        return violations;
    }

    public static void main(String[] args) {
        // Mechanical background isolation via COMMAND-LINE arguments: Spring
        // Boot resolves command-line args as the highest-precedence property
        // source, so an operator environment variable such as
        // KBASE_AI_WORKER_SCHEDULING_ENABLED=true cannot override it (unlike
        // the previous DefaultProperties layer, which sat BELOW the OS
        // environment). The effective value is asserted before any provider
        // call, so isolation is deterministic, not timing-based.
        ConfigurableApplicationContext context = new SpringApplicationBuilder(KBaseApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("local")
                .run(
                        "--server.port=18085",
                        "--kbase.ai.worker.scheduling-enabled=false");
        try {
            var environment = context.getEnvironment();
            String providerMode = environment.getProperty("kbase.ai.provider.mode");
            String chatModel = environment.getProperty("kbase.ai.gemini.chat-model");
            String embeddingModel = environment.getProperty("kbase.ai.gemini.embedding-model");
            String dimensionsProperty = environment.getProperty("kbase.ai.gemini.embedding-dimensions");
            String effectiveScheduling = environment.getProperty("kbase.ai.worker.scheduling-enabled");

            // --- Isolation preflight: the override must be effective ---
            System.out.println("[smoke] effectiveSchedulingEnabled=" + effectiveScheduling
                    + " (must be false regardless of external environment)");
            if (!"false".equals(effectiveScheduling)) {
                System.out.println("[smoke] RESULT=FAIL (isolation override was not effective; "
                        + "no provider request was sent)");
                System.exit(1);
            }

            // --- Candidate preflight: fail BEFORE any provider invocation on config drift ---
            List<String> violations = candidateViolations(
                    providerMode, chatModel, embeddingModel, dimensionsProperty);
            System.out.println("[smoke] preflight provider.mode=" + providerMode
                    + " chatModel=" + chatModel
                    + " embeddingModel=" + embeddingModel
                    + " embeddingDimensions=" + dimensionsProperty
                    + " violations=" + violations.size());
            if (!violations.isEmpty()) {
                violations.forEach(violation -> System.out.println("[smoke] PREFLIGHT_VIOLATION " + violation));
                System.out.println("[smoke] RESULT=FAIL (no provider request was sent)");
                System.exit(1);
            }

            // --- Isolation assertion: scheduling infrastructure must be absent ---
            boolean schedulerActive = context.containsBean(SCHEDULED_ANNOTATION_PROCESSOR_BEAN);
            System.out.println("[smoke] backgroundSchedulingActive=" + schedulerActive
                    + " (must be false; AiJobScheduler cannot claim jobs in this context)");
            if (schedulerActive) {
                System.out.println("[smoke] RESULT=FAIL (background scheduling still active; "
                        + "no provider request was sent)");
                System.exit(1);
            }

            // --- Chat adapter smoke (direct port, no RAG/Project Assistant path) ---
            boolean fail = false;
            try {
                AiChatModel chat = context.getBean(AiChatModel.class);
                if (!(chat instanceof com.kbase.ai.provider.springai.SpringAiGeminiChatAdapter)) {
                    System.out.println("[smoke] CHAT_FAIL type=" + chat.getClass().getName()
                            + " (expected the Gemini KBase adapter)");
                    fail = true;
                } else {
                    AiChatResult chatResult = chat.generate(AiChatRequest.of(
                            "Reply exactly with: KBASE_GEMINI_OK"));
                    String normalized = chatResult.text() == null ? "" : chatResult.text().trim();
                    System.out.println("[smoke] CHAT_OK modelId=" + chatResult.modelId()
                            + " responseChars=" + normalized.length()
                            + " response=" + normalized);
                }
            } catch (RuntimeException chatFailure) {
                // Safe failure only: KBase category/type — no raw vendor body.
                System.out.println("[smoke] CHAT_FAIL safeCategory="
                        + providerCategory(chatFailure)
                        + " safeType=" + chatFailure.getClass().getName());
                fail = true;
            }

            // --- Embedding adapter smoke (direct port, no indexing/corpus write) ---
            try {
                AiEmbeddingModel embedding = context.getBean(AiEmbeddingModel.class);
                if (!(embedding instanceof com.kbase.ai.provider.springai.SpringAiGeminiEmbeddingAdapter)) {
                    System.out.println("[smoke] EMBED_FAIL type=" + embedding.getClass().getName()
                            + " (expected the Gemini KBase adapter)");
                    fail = true;
                } else {
                    AiEmbeddingResult embeddingResult = embedding.embed(new AiEmbeddingRequest(
                            EmbeddingMode.QUERY, "KBase real Gemini embedding smoke test"));
                    boolean finite = embeddingResult.vector().stream().allMatch(Double::isFinite);
                    System.out.println("[smoke] EMBED_OK modelId=" + embeddingResult.modelId()
                            + " dimensions=" + embeddingResult.dimensions()
                            + " allFinite=" + finite
                            + " dimensionsMatch=" + (embeddingResult.dimensions()
                                    == Integer.parseInt(EXPECTED_EMBEDDING_DIMENSIONS)));
                    if (embeddingResult.dimensions() != Integer.parseInt(EXPECTED_EMBEDDING_DIMENSIONS)
                            || !finite) {
                        fail = true;
                    }
                }
            } catch (RuntimeException embeddingFailure) {
                System.out.println("[smoke] EMBED_FAIL safeCategory="
                        + providerCategory(embeddingFailure)
                        + " safeType=" + embeddingFailure.getClass().getName());
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

    private static String providerCategory(RuntimeException failure) {
        if (failure instanceof com.kbase.ai.provider.error.AiProviderException providerException) {
            return providerException.category().name();
        }
        return "UNEXPECTED_" + failure.getClass().getSimpleName();
    }
}
