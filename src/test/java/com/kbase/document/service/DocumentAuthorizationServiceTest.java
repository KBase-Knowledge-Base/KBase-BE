package com.kbase.document.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.kbase.document.entity.Document;
import com.kbase.project.entity.Project;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.project.service.ProjectAuthorizationService.ProjectAccess;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.KBaseException;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentAuthorizationServiceTest {
    private ProjectAuthorizationService projectAuthorization;
    private DocumentAuthorizationService service;
    private Document document;
    private CustomUserPrincipal uploader;
    private CustomUserPrincipal otherMember;

    @BeforeEach
    void setUp() {
        projectAuthorization = mock(ProjectAuthorizationService.class);
        service = new DocumentAuthorizationService(projectAuthorization);
        Project project = new Project("P", null); project.setId(UUID.randomUUID());
        User user = new User("a@example.com", "hash", "A", SystemRole.USER, UserStatus.ACTIVE);
        user.setId(UUID.randomUUID());
        document = new Document(project, user, "a.pdf", "a.pdf", com.kbase.document.enums.FileKind.DOCUMENT,
                "pdf", "application/pdf", 1, "projects/a/documents/b.pdf");
        uploader = principal(user.getId(), SystemRole.USER);
        otherMember = principal(UUID.randomUUID(), SystemRole.USER);
    }

    @Test
    void membersReadAllButOnlyUploaderMutatesAndOwnerAdminOverride() {
        when(projectAuthorization.requireProjectAccess(document.getProject().getId(), uploader))
                .thenReturn(new ProjectAccess(document.getProject(), ProjectRole.MEMBER, false));
        when(projectAuthorization.requireProjectAccess(document.getProject().getId(), otherMember))
                .thenReturn(new ProjectAccess(document.getProject(), ProjectRole.MEMBER, false));
        assertThatCode(() -> service.requireReadPermission(document, otherMember)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireModifyPermission(document, uploader)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireModifyPermission(document, otherMember))
                .isInstanceOf(KBaseException.class)
                .extracting(error -> ((KBaseException) error).getErrorCode().code())
                .isEqualTo("DOCUMENT_MODIFICATION_FORBIDDEN");

        CustomUserPrincipal owner = principal(UUID.randomUUID(), SystemRole.USER);
        when(projectAuthorization.requireProjectAccess(document.getProject().getId(), owner))
                .thenReturn(new ProjectAccess(document.getProject(), ProjectRole.OWNER, false));
        assertThatCode(() -> service.requireModifyPermission(document, owner)).doesNotThrowAnyException();
        CustomUserPrincipal admin = principal(UUID.randomUUID(), SystemRole.ADMIN);
        when(projectAuthorization.requireProjectAccess(document.getProject().getId(), admin))
                .thenReturn(new ProjectAccess(document.getProject(), null, true));
        assertThatCode(() -> service.requireModifyPermission(document, admin)).doesNotThrowAnyException();
    }

    private static CustomUserPrincipal principal(UUID id, SystemRole role) {
        return new CustomUserPrincipal(id, id + "@example.com", role, UserStatus.ACTIVE, true);
    }
}
