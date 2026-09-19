package com.kbase.category.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.config.OpenApiConfig;
import com.kbase.category.dto.request.CreateCategoryRequest;
import com.kbase.category.dto.request.UpdateCategoryRequest;
import com.kbase.category.dto.response.CategoryResponse;
import com.kbase.category.service.CategoryService;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.response.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

/** REST boundary for project-scoped categories. */
@Tag(name = OpenApiConfig.TAG_CATEGORIES,
        description = "Project-scoped categories. Reading is open to every project member; creating, renaming and "
                + "deleting are OWNER/ADMIN managed.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
@RestController
@RequestMapping("/api/v1/projects/{projectId}/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final CurrentUserService currentUserService;

    public CategoryController(CategoryService categoryService, CurrentUserService currentUserService) {
        this.categoryService = categoryService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "List categories",
            description = "Access: project MEMBER, OWNER, or system ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categories of the project"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller is not a member of the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories(@PathVariable UUID projectId) {
        return ResponseEntity.ok(categoryService.listCategories(
                projectId, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Create category",
            description = "OWNER/ADMIN only. Names are unique per project case-insensitively.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Category created"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — the name is blank or too long",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — caller is not a member, or "
                    + "PROJECT_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN manage categories",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CATEGORY_NAME_ALREADY_EXISTS — case-insensitive duplicate in the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(
                projectId, request, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Rename category",
            description = "OWNER/ADMIN only. Names are unique per project case-insensitively.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category renamed"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — caller is not a member, or "
                    + "PROJECT_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN manage categories",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CATEGORY_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CATEGORY_NAME_ALREADY_EXISTS — case-insensitive duplicate in the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> renameCategory(
            @PathVariable UUID projectId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.renameCategory(
                projectId, categoryId, request, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Delete category",
            description = "OWNER/ADMIN only. A category still used by any document cannot be deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Category deleted; no response body"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — caller is not a member, or "
                    + "PROJECT_MANAGEMENT_FORBIDDEN — only OWNER/ADMIN manage categories",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CATEGORY_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CATEGORY_IN_USE — one or more documents still use the category",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable UUID projectId,
            @PathVariable UUID categoryId) {
        categoryService.deleteCategory(projectId, categoryId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }
}
