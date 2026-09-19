package com.kbase.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.kbase.document.entity.Document;
import com.kbase.document.enums.FileKind;
import com.kbase.document.mapper.DocumentMapper;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.project.entity.Project;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DocumentSearchServiceTest {

    private DocumentRepository documents;
    private ProjectAuthorizationService authorization;
    private DocumentSearchService service;
    private CustomUserPrincipal principal;

    @BeforeEach
    void setUp() {
        documents = mock(DocumentRepository.class);
        authorization = mock(ProjectAuthorizationService.class);
        service = new DocumentSearchService(documents, authorization, new DocumentMapper());
        principal = new CustomUserPrincipal(UUID.randomUUID(), "member@example.com", SystemRole.USER,
                UserStatus.ACTIVE, true);
    }

    @Test
    void authorizesThenRunsTheProjectScopedSpecificationAndMapsSummary() {
        UUID projectId = UUID.randomUUID();
        Document document = new Document(new Project("Search", null),
                new User("uploader@example.com", "hash", "Uploader", SystemRole.USER, UserStatus.ACTIVE),
                "Architecture.pdf", "architecture.pdf", FileKind.DOCUMENT, "pdf", "application/pdf", 42,
                "internal/key");
        document.setId(UUID.randomUUID());
        document.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        document.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        when(documents.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                eq(PageRequest.of(0, 20)))).thenReturn(new PageImpl<>(java.util.List.of(document),
                PageRequest.of(0, 20), 1));

        var response = service.search(projectId, new DocumentSearchCriteria("architecture", null, null,
                null, FileKind.DOCUMENT, null, null, null), principal, PageRequest.of(0, 20));

        verify(authorization).requireProjectAccess(projectId, principal);
        verify(documents).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                eq(PageRequest.of(0, 20)));
        assertThat(response.content()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(document.getId());
            assertThat(summary.displayName()).isEqualTo("Architecture.pdf");
        });
    }

    @Test
    void doesNotQueryWhenProjectAccessIsDenied() {
        UUID projectId = UUID.randomUUID();
        when(authorization.requireProjectAccess(eq(projectId), eq(principal)))
                .thenThrow(new BusinessException(ErrorCode.PROJECT_ACCESS_FORBIDDEN));

        assertThrows(BusinessException.class, () -> service.search(projectId,
                new DocumentSearchCriteria(null, null, null, null, null, null, null, null),
                principal, PageRequest.of(0, 20)));

        verify(documents, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }
}
