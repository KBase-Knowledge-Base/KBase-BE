package com.kbase.category.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.kbase.category.dto.request.CreateCategoryRequest;
import com.kbase.category.dto.request.UpdateCategoryRequest;
import com.kbase.category.dto.response.CategoryResponse;
import com.kbase.category.entity.Category;
import com.kbase.category.mapper.CategoryMapper;
import com.kbase.category.repository.CategoryRepository;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;

import jakarta.validation.Valid;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project-scoped category lifecycle managed by OWNER/ADMIN. */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final DocumentRepository documentRepository;
    private final ProjectAuthorizationService authorizationService;
    private final CategoryMapper categoryMapper;

    public CategoryService(
            CategoryRepository categoryRepository,
            DocumentRepository documentRepository,
            ProjectAuthorizationService authorizationService,
            CategoryMapper categoryMapper) {
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.documentRepository = Objects.requireNonNull(documentRepository, "documentRepository");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.categoryMapper = Objects.requireNonNull(categoryMapper, "categoryMapper");
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(UUID projectId, CustomUserPrincipal principal) {
        authorizationService.requireProjectAccess(projectId, principal);
        return categoryRepository.findAllByProjectIdOrderByNameAsc(projectId)
                .stream().map(categoryMapper::toResponse).toList();
    }

    @Transactional
    public CategoryResponse createCategory(
            UUID projectId, @Valid CreateCategoryRequest request, CustomUserPrincipal principal) {
        Objects.requireNonNull(request, "request");
        ProjectAuthorizationService.ProjectAccess access =
                authorizationService.requireOwner(projectId, principal);
        String name = normalizeName(request.name());
        ensureUnique(projectId, name, null);
        return categoryMapper.toResponse(categoryRepository.save(new Category(access.project(), name)));
    }

    @Transactional
    public CategoryResponse renameCategory(
            UUID projectId,
            UUID categoryId,
            @Valid UpdateCategoryRequest request,
            CustomUserPrincipal principal) {
        Objects.requireNonNull(request, "request");
        authorizationService.requireOwner(projectId, principal);
        Category category = categoryRepository.findByIdAndProjectId(categoryId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        String name = normalizeName(request.name());
        ensureUnique(projectId, name, categoryId);
        category.setName(name);
        return categoryMapper.toResponse(category);
    }

    @Transactional
    public void deleteCategory(UUID projectId, UUID categoryId, CustomUserPrincipal principal) {
        authorizationService.requireOwner(projectId, principal);
        Category category = categoryRepository.findByIdAndProjectId(categoryId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (documentRepository.existsByCategoryId(category.getId())) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
        }
        categoryRepository.delete(category);
    }

    private void ensureUnique(UUID projectId, String name, UUID excludedCategoryId) {
        boolean duplicate = excludedCategoryId == null
                ? categoryRepository.existsByProjectIdAndNameIgnoreCase(projectId, name)
                : categoryRepository.existsByProjectIdAndNameIgnoreCaseAndIdNot(
                        projectId, name, excludedCategoryId);
        if (duplicate) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        }
    }

    private static String normalizeName(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Category name is required.");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Category name is required.");
        }
        return normalized;
    }
}
