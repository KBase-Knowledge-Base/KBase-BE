package com.kbase.ai.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.ai.dto.request.CreateAiConversationRequest;
import com.kbase.ai.dto.request.RenameAiConversationRequest;
import com.kbase.ai.dto.request.SendAiMessageRequest;
import com.kbase.ai.dto.response.AiConversationResponse;
import com.kbase.ai.dto.response.AiTurnResponse;
import com.kbase.ai.dto.response.CreateAiConversationResponse;
import com.kbase.ai.service.ProjectAssistantConversationService;
import com.kbase.config.OpenApiConfig;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.shared.pagination.PaginationParser;
import com.kbase.shared.response.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, creator-private Project Assistant HTTP boundary. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/ai/conversations")
@Tag(name = OpenApiConfig.TAG_AI_PROJECT_ASSISTANT,
        description = "Private Project Assistant conversations. Current project access and creator ownership "
                + "are both required; ADMIN project override never bypasses conversation ownership.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
public class ProjectAssistantController {

    private static final Sort CONVERSATION_ORDER = Sort.by(Sort.Order.desc("updatedAt"),
            Sort.Order.desc("id"));
    private static final Sort MESSAGE_ORDER = Sort.by(Sort.Order.asc("createdAt"),
            Sort.Order.asc("id"));

    private final ProjectAssistantConversationService service;
    private final CurrentUserService currentUser;
    private final PaginationParser pagination;

    public ProjectAssistantController(ProjectAssistantConversationService service,
            CurrentUserService currentUser, PaginationParser pagination) {
        this.service = service;
        this.currentUser = currentUser;
        this.pagination = pagination;
    }

    @Operation(summary = "Create private conversation with first question",
            description = "Creates only when the first valid message arrives. Maximum five conversations per user "
                    + "and project. Persists USER and ASSISTANT PROCESSING before non-streaming generation. "
                    + "Returns GROUNDED with sources or successful NO_EVIDENCE with no sources. "
                    + "Provider failure leaves the conversation and a safely FAILED assistant message readable.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversation and first completed turn"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AI_CONVERSATION_LIMIT_REACHED",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "AI_PROVIDER_UNAVAILABLE",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<CreateAiConversationResponse> create(@PathVariable UUID projectId,
            @Valid @RequestBody CreateAiConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId,
                currentUser.requirePrincipal(), request.message()));
    }

    @Operation(summary = "List own private conversations",
            description = "Only the current creator's conversations, ordered updatedAt DESC then id DESC. "
                    + "Current project access is required before any conversation query.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged conversation metadata"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<PageResponse<AiConversationResponse>> list(@PathVariable UUID projectId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = pagination.parse(page, size, null, List.of(), CONVERSATION_ORDER);
        return ResponseEntity.ok(service.list(projectId, currentUser.requirePrincipal(), pageable));
    }

    @Operation(summary = "Get own conversation metadata",
            description = "Messages are served by the separate paginated route. Wrong project, missing ID "
                    + "and another creator's ID all return AI_CONVERSATION_NOT_FOUND after project access check.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversation metadata"),
            @ApiResponse(responseCode = "404", description = "AI_CONVERSATION_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{conversationId}")
    public ResponseEntity<AiConversationResponse> get(@PathVariable UUID projectId,
            @PathVariable UUID conversationId) {
        return ResponseEntity.ok(service.get(projectId, conversationId, currentUser.requirePrincipal()));
    }

    @Operation(summary = "Rename own conversation",
            description = "Creator only; trim title, require nonblank and at most 100 Unicode characters. "
                    + "A rename updates the list activity timestamp without provider work.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated conversation metadata"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "AI_CONVERSATION_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{conversationId}")
    public ResponseEntity<AiConversationResponse> rename(@PathVariable UUID projectId,
            @PathVariable UUID conversationId,
            @Valid @RequestBody RenameAiConversationRequest request) {
        return ResponseEntity.ok(service.rename(projectId, conversationId,
                currentUser.requirePrincipal(), request.title()));
    }

    @Operation(summary = "Hard delete own conversation",
            description = "Creator only; messages and sources cascade, and the deleted row immediately frees quota.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Conversation removed"),
            @ApiResponse(responseCode = "404", description = "AI_CONVERSATION_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId,
            @PathVariable UUID conversationId) {
        service.delete(projectId, conversationId, currentUser.requirePrincipal());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send a question to own conversation",
            description = "Persists USER and ASSISTANT PROCESSING atomically before M6 RAG outside the transaction. "
                    + "Only one active generation per conversation; a competing send gets "
                    + "AI_REQUEST_IN_PROGRESS without a second USER message. GROUNDED and NO_EVIDENCE "
                    + "are successful non-streaming outcomes; current access is rechecked before completion.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completed assistant turn and ordered sources"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "AI_CONVERSATION_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AI_REQUEST_IN_PROGRESS",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "AI_PROVIDER_UNAVAILABLE",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<AiTurnResponse> send(@PathVariable UUID projectId,
            @PathVariable UUID conversationId, @Valid @RequestBody SendAiMessageRequest request) {
        return ResponseEntity.ok(service.send(projectId, conversationId,
                currentUser.requirePrincipal(), request.message()));
    }

    @Operation(summary = "List own conversation messages",
            description = "Paged history ordered createdAt ASC then id ASC. Historical sources retain snapshots; "
                    + "deleted document/chunk references are UNAVAILABLE with no usable documentId. "
                    + "Live source opening still uses current Core document authorization. Failed generations "
                    + "show a safe failureCode and no provider text.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged messages with sources"),
            @ApiResponse(responseCode = "404", description = "AI_CONVERSATION_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<PageResponse<AiTurnResponse>> messages(@PathVariable UUID projectId,
            @PathVariable UUID conversationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = pagination.parse(page, size == null ? 50 : size, null,
                List.of(), MESSAGE_ORDER);
        return ResponseEntity.ok(service.listMessages(projectId, conversationId,
                currentUser.requirePrincipal(), pageable));
    }
}
