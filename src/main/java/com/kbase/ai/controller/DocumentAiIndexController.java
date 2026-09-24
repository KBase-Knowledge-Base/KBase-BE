package com.kbase.ai.controller;

import java.util.UUID;

import com.kbase.ai.dto.response.DocumentAiIndexResponse;
import com.kbase.ai.service.DocumentAiIndexApplicationService;
import com.kbase.config.OpenApiConfig;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.response.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public projection of the existing M5 document-index application boundary. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/documents/{documentId}/ai-index")
@Tag(name = OpenApiConfig.TAG_AI_DOCUMENT_INDEXING,
        description = "Document AI indexing status and asynchronous FAILED-only retry. Read and modify "
                + "permissions follow the existing Core document policy.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
public class DocumentAiIndexController {

    private final DocumentAiIndexApplicationService service;
    private final CurrentUserService currentUser;

    public DocumentAiIndexController(DocumentAiIndexApplicationService service,
            CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Get document AI index status",
            description = "Requires current project/document read access. Returns PENDING, PROCESSING, "
                    + "READY, FAILED or UNSUPPORTED and only a safe failure reason; no source hash, "
                    + "job state, vector or storage key is exposed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Safe document index status"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "DOCUMENT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<DocumentAiIndexResponse> status(@PathVariable UUID projectId,
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(DocumentAiIndexResponse.from(service.getStatus(
                projectId, documentId, currentUser.requirePrincipal())));
    }

    @Operation(summary = "Retry failed document AI index asynchronously",
            description = "Only FAILED can be retried. MEMBER uploader, project OWNER or system ADMIN "
                    + "may request a durable retry. The request does not synchronously call the provider.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Retry accepted; current safe status"),
            @ApiResponse(responseCode = "403", description = "DOCUMENT_MODIFICATION_FORBIDDEN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "DOCUMENT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AI_INDEX_RETRY_NOT_ALLOWED",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/retry")
    public ResponseEntity<DocumentAiIndexResponse> retry(@PathVariable UUID projectId,
            @PathVariable UUID documentId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentAiIndexResponse.from(
                service.requestManualRetry(projectId, documentId, currentUser.requirePrincipal())));
    }
}
