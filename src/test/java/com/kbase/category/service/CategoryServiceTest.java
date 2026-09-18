package com.kbase.category.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.kbase.category.dto.request.CreateCategoryRequest;
import com.kbase.category.dto.request.UpdateCategoryRequest;
import com.kbase.category.entity.Category;
import com.kbase.category.mapper.CategoryMapper;
import com.kbase.category.repository.CategoryRepository;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.project.entity.Project;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ProjectAuthorizationService authorizationService;
    @Mock
    private CategoryMapper categoryMapper;

    private CategoryService service;
    private Project project;
    private CustomUserPrincipal owner;
    private CustomUserPrincipal member;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, documentRepository,
                authorizationService, categoryMapper);
        project = new Project("Project", null);
        project.setId(UUID.randomUUID());
        owner = principal();
        member = principal();
        lenient().when(authorizationService.requireOwner(eq(project.getId()), eq(owner)))
                .thenReturn(new ProjectAuthorizationService.ProjectAccess(
                        project, ProjectRole.OWNER, false));
    }

    @Test
    void memberCannotManageCategory() {
        when(authorizationService.requireOwner(eq(project.getId()), eq(member)))
                .thenThrow(new BusinessException(ErrorCode.PROJECT_MANAGEMENT_FORBIDDEN));

        assertThatThrownBy(() -> service.createCategory(
                project.getId(), new CreateCategoryRequest("Technical"), member))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.PROJECT_MANAGEMENT_FORBIDDEN);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void duplicateNamesAreRejectedAndRenameExcludesCurrent() {
        when(categoryRepository.existsByProjectIdAndNameIgnoreCase(
                project.getId(), "Technical")).thenReturn(true);
        assertThatThrownBy(() -> service.createCategory(
                project.getId(), new CreateCategoryRequest(" Technical "), owner))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);

        Category category = new Category(project, "Technical");
        category.setId(UUID.randomUUID());
        when(categoryRepository.findByIdAndProjectId(category.getId(), project.getId()))
                .thenReturn(Optional.of(category));
        when(categoryRepository.existsByProjectIdAndNameIgnoreCaseAndIdNot(
                project.getId(), "Architecture", category.getId())).thenReturn(false);
        when(categoryMapper.toResponse(category)).thenReturn(null);
        service.renameCategory(project.getId(), category.getId(),
                new UpdateCategoryRequest(" Architecture "), owner);
        verify(categoryRepository).existsByProjectIdAndNameIgnoreCaseAndIdNot(
                project.getId(), "Architecture", category.getId());
    }

    @Test
    void inUseCategoryCannotBeDeleted() {
        Category category = new Category(project, "Technical");
        category.setId(UUID.randomUUID());
        when(categoryRepository.findByIdAndProjectId(category.getId(), project.getId()))
                .thenReturn(Optional.of(category));
        when(documentRepository.existsByCategoryId(category.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteCategory(project.getId(), category.getId(), owner))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.CATEGORY_IN_USE);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void unusedCategoryCanBeDeleted() {
        Category category = new Category(project, "Technical");
        category.setId(UUID.randomUUID());
        when(categoryRepository.findByIdAndProjectId(category.getId(), project.getId()))
                .thenReturn(Optional.of(category));
        when(documentRepository.existsByCategoryId(category.getId())).thenReturn(false);

        service.deleteCategory(project.getId(), category.getId(), owner);
        verify(categoryRepository).delete(category);
    }

    private static CustomUserPrincipal principal() {
        return new CustomUserPrincipal(UUID.randomUUID(), UUID.randomUUID() + "@example.com",
                SystemRole.USER, UserStatus.ACTIVE, true);
    }
}
