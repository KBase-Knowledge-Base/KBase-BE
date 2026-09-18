package com.kbase.category.controller;

import java.util.List;
import java.util.UUID;

import com.kbase.category.dto.request.CreateCategoryRequest;
import com.kbase.category.dto.request.UpdateCategoryRequest;
import com.kbase.category.dto.response.CategoryResponse;
import com.kbase.category.service.CategoryService;
import com.kbase.security.service.CurrentUserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
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
@RestController
@RequestMapping("/api/v1/projects/{projectId}/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final CurrentUserService currentUserService;

    public CategoryController(CategoryService categoryService, CurrentUserService currentUserService) {
        this.categoryService = categoryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories(@PathVariable UUID projectId) {
        return ResponseEntity.ok(categoryService.listCategories(
                projectId, currentUserService.requirePrincipal()));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(
                projectId, request, currentUserService.requirePrincipal()));
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> renameCategory(
            @PathVariable UUID projectId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.renameCategory(
                projectId, categoryId, request, currentUserService.requirePrincipal()));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable UUID projectId,
            @PathVariable UUID categoryId) {
        categoryService.deleteCategory(projectId, categoryId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }
}
