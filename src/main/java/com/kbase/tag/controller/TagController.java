package com.kbase.tag.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.config.OpenApiConfig;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.response.ApiErrorResponse;
import com.kbase.tag.dto.request.CreateTagRequest;
import com.kbase.tag.dto.request.UpdateTagRequest;
import com.kbase.tag.dto.response.TagResponse;
import com.kbase.tag.service.TagService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

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

/** REST boundary for project-scoped tags. */
@Tag(name = OpenApiConfig.TAG_TAGS,
        description = "Project-scoped shared tags. Any project member may create and read tags; renaming and "
                + "deleting are OWNER/ADMIN managed. Deleting a tag only removes its document assignments, never documents.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tags")
public class TagController {

    private final TagService tagService;
    private final CurrentUserService currentUserService;

    public TagController(TagService tagService, CurrentUserService currentUserService) {
        this.tagService = tagService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "List tags",
            description = "Access: project MEMBER, OWNER, or system ADMIN. With q, filters by case-insensitive name substring.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tags of the project"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller is not a member of the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<TagResponse>> listTags(
            @PathVariable UUID projectId,
            @Parameter(description = "Optional case-insensitive name substring filter")
            @RequestParam(name = "q", required = false) String query) {
        return ResponseEntity.ok(tagService.listTags(
                projectId, query, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Create tag",
            description = "Any project member (MEMBER, OWNER or system ADMIN) may create a shared tag. "
                    + "Names are unique per project case-insensitively.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tag created"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — the name is blank or too long",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller is not a member of the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "TAG_NAME_ALREADY_EXISTS — case-insensitive duplicate in the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<TagResponse> createTag(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.createTag(
                projectId, request, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Rename tag",
            description = "OWNER/ADMIN only; a MEMBER cannot rename a shared tag.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tag renamed"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — caller is not a member, or "
                    + "TAG_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN rename tags",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "TAG_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "TAG_NAME_ALREADY_EXISTS — case-insensitive duplicate in the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{tagId}")
    public ResponseEntity<TagResponse> renameTag(
            @PathVariable UUID projectId,
            @PathVariable UUID tagId,
            @Valid @RequestBody UpdateTagRequest request) {
        return ResponseEntity.ok(tagService.renameTag(
                projectId, tagId, request, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Delete tag",
            description = "OWNER/ADMIN only. Deletes the tag and its document tag assignments only; documents are kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tag and its assignments deleted; documents remain; no response body"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — caller is not a member, or "
                    + "TAG_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN delete tags",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "TAG_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> deleteTag(
            @PathVariable UUID projectId,
            @PathVariable UUID tagId) {
        tagService.deleteTag(projectId, tagId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }
}
