package com.kbase.ai.controller;

import java.util.List;

import com.kbase.ai.dto.request.GuideContextMessage;
import com.kbase.ai.dto.request.GuideQueryRequest;
import com.kbase.ai.dto.response.GuideQueryResponse;
import com.kbase.ai.observability.AiObservability;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.model.AiChatMessage;
import com.kbase.ai.retrieval.GuideRagService;
import com.kbase.ai.usage.AiUsageGuard;
import com.kbase.config.OpenApiConfig;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.response.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated, stateless product-help endpoint. It intentionally has no project route. */
@RestController
@RequestMapping("/api/v1/ai/guide")
@Tag(name = OpenApiConfig.TAG_AI_GUIDE, description = "Stateless KBase Guide grounded only in approved product specifications.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
public class GuideController {
    private final ObjectProvider<GuideRagService> guide;
    private final CurrentUserService currentUser;
    private final AiUsageGuard usageGuard;
    private final AiObservability observability;

    public GuideController(ObjectProvider<GuideRagService> guide, CurrentUserService currentUser,
            AiUsageGuard usageGuard, AiObservability observability) {
        this.guide = guide;
        this.currentUser = currentUser;
        this.usageGuard = usageGuard;
        this.observability = observability;
    }

    @Operation(summary = "Query the stateless KBase Guide", description = "Accepts only bounded USER/ASSISTANT context; no context is persisted. Retrieval uses only approved Guide sources and unsupported questions return NO_EVIDENCE.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "GROUNDED or NO_EVIDENCE response"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "AI_RATE_LIMIT_EXCEEDED", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "AI_PROVIDER_UNAVAILABLE or AI_USAGE_GUARD_UNAVAILABLE", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))) })
    @PostMapping("/query")
    public ResponseEntity<GuideQueryResponse> query(@Valid @RequestBody GuideQueryRequest request) {
        long startedAt = System.nanoTime();
        String outcome = "SUCCESS";
        try {
            var principal = currentUser.requirePrincipal(); // authenticated even though Guide has no project scope
            GuideRagService service = guide.getIfAvailable();
            if (service == null) throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
            usageGuard.consume(principal.getUserId());
            List<AiChatMessage> context = request.context().stream().map(this::context).toList();
            return ResponseEntity.ok(service.answer(request.message(), context));
        } catch (AiProviderException failure) {
            outcome = "PROVIDER_UNAVAILABLE";
            throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        } catch (RuntimeException failure) {
            outcome = AiObservability.requestOutcome(failure);
            throw failure;
        } finally {
            observability.recordInteractiveRequest("GUIDE_QUERY", outcome,
                    System.nanoTime() - startedAt);
        }
    }
    private AiChatMessage context(GuideContextMessage turn) { return new AiChatMessage(turn.role(), turn.content()); }
}
