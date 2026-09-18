package com.kbase.folder.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.config.OpenApiConfig;
import com.kbase.folder.dto.request.CreateFolderRequest;
import com.kbase.folder.dto.request.UpdateFolderRequest;
import com.kbase.folder.dto.response.FolderResponse;
import com.kbase.folder.service.FolderService;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.response.ApiErrorResponse;

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

/** REST boundary for project-scoped folder hierarchy. */
@Tag(name = OpenApiConfig.TAG_FOLDERS,
        description = "Project-scoped folder hierarchy. Reading is open to every project member; creating, "
                + "renaming and moving are OWNER/ADMIN managed.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
@RestController
@RequestMapping("/api/v1/projects/{projectId}/folders")
public class FolderController {

    private final FolderService folderService;
    private final CurrentUserService currentUserService;

    public FolderController(FolderService folderService, CurrentUserService currentUserService) {
        this.folderService = folderService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "List folders",
            description = "Access: project MEMBER, OWNER, or system ADMIN. Returns the project's folder tree flattened; "
                    + "with parentId it returns that folder's children.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Folders of the project or of the requested parent"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller is not a member of the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND or PARENT_FOLDER_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<FolderResponse>> listFolders(
            @PathVariable UUID projectId,
            @Parameter(description = "Optional parent folder id; omit to start from the root level")
            @RequestParam(name = "parentId", required = false) UUID parentId) {
        return ResponseEntity.ok(folderService.listFolders(
                projectId, parentId, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Create folder",
            description = "Creates a root or nested folder. OWNER/ADMIN only. The parent must belong to the same "
                    + "project and sibling names are unique case-insensitively.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Folder created"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — the name is blank or too long",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — caller is not a member, or "
                    + "PROJECT_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN manage folders",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND or PARENT_FOLDER_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "FOLDER_NAME_ALREADY_EXISTS — a case-insensitive sibling with this name exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateFolderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(folderService.createFolder(
                projectId, request, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Rename or move folder",
            description = "OWNER/ADMIN only. The new parent must belong to the same project, must not be the folder "
                    + "itself or one of its descendants (no cycles), and the target sibling name must be unique "
                    + "case-insensitively excluding the folder itself.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Folder renamed or moved"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — caller is not a member, or "
                    + "PROJECT_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN manage folders",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "FOLDER_NOT_FOUND or PARENT_FOLDER_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "FOLDER_CYCLE_DETECTED — moving into itself/descendant, or "
                    + "FOLDER_NAME_ALREADY_EXISTS — duplicate case-insensitive sibling",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{folderId}")
    public ResponseEntity<FolderResponse> updateFolder(
            @PathVariable UUID projectId,
            @PathVariable UUID folderId,
            @Valid @RequestBody UpdateFolderRequest request) {
        return ResponseEntity.ok(folderService.updateFolder(
                projectId, folderId, request, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Delete folder",
            description = "OWNER/ADMIN only. Only an empty folder (no child folders, no documents) can be deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Folder deleted; no response body"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — caller is not a member, or "
                    + "PROJECT_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN manage folders",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "FOLDER_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "FOLDER_NOT_EMPTY — the folder still contains subfolders or documents",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable UUID projectId,
            @PathVariable UUID folderId) {
        folderService.deleteFolder(projectId, folderId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }
}
