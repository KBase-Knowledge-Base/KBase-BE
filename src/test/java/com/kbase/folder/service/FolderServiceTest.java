package com.kbase.folder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.kbase.document.repository.DocumentRepository;
import com.kbase.folder.dto.request.CreateFolderRequest;
import com.kbase.folder.dto.request.UpdateFolderRequest;
import com.kbase.folder.entity.Folder;
import com.kbase.folder.mapper.FolderMapper;
import com.kbase.folder.repository.FolderRepository;
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
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ProjectAuthorizationService authorizationService;
    @Mock
    private FolderMapper folderMapper;

    private FolderService service;
    private Project project;
    private CustomUserPrincipal owner;
    private CustomUserPrincipal member;

    @BeforeEach
    void setUp() {
        service = new FolderService(folderRepository, documentRepository, authorizationService, folderMapper);
        project = new Project("Project", null);
        project.setId(UUID.randomUUID());
        owner = principal(SystemRole.USER);
        member = principal(SystemRole.USER);
        lenient().when(authorizationService.requireOwner(eq(project.getId()), any()))
                .thenReturn(new ProjectAuthorizationService.ProjectAccess(
                        project, ProjectRole.OWNER, false));
    }

    @Test
    void memberCannotCreateFolder() {
        when(authorizationService.requireOwner(eq(project.getId()), eq(member)))
                .thenThrow(new BusinessException(ErrorCode.PROJECT_MANAGEMENT_FORBIDDEN));

        assertThatThrownBy(() -> service.createFolder(
                project.getId(), new CreateFolderRequest("Docs", null), member))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.PROJECT_MANAGEMENT_FORBIDDEN);
        verify(folderRepository, never()).save(any());
    }

    @Test
    void duplicateSiblingIsRejectedCaseInsensitively() {
        when(folderRepository.existsByProjectIdAndParentIdIsNullAndNameIgnoreCase(
                project.getId(), "Docs")).thenReturn(true);

        assertThatThrownBy(() -> service.createFolder(
                project.getId(), new CreateFolderRequest(" Docs ", null), owner))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.FOLDER_NAME_ALREADY_EXISTS);
    }

    @Test
    void crossProjectParentIsRejected() {
        UUID parentId = UUID.randomUUID();
        when(folderRepository.findByIdAndProjectId(parentId, project.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFolder(
                project.getId(), new CreateFolderRequest("Docs", parentId), owner))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.PARENT_FOLDER_NOT_FOUND);
    }

    @Test
    void sameNameUnderDifferentParentIsAllowed() {
        Folder parent = folder(UUID.randomUUID(), null, "Parent");
        when(folderRepository.findByIdAndProjectId(parent.getId(), project.getId()))
                .thenReturn(Optional.of(parent));
        when(folderRepository.existsByProjectIdAndParentIdAndNameIgnoreCase(
                project.getId(), parent.getId(), "Docs")).thenReturn(false);
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(folderMapper.toResponse(any(Folder.class))).thenReturn(null);

        assertThat(service.createFolder(
                project.getId(), new CreateFolderRequest(" Docs ", parent.getId()), owner)).isNull();
        verify(folderRepository).save(any(Folder.class));
    }

    @Test
    void selfParentAndDescendantMovesAreRejectedByAncestorWalk() {
        UUID folderId = UUID.randomUUID();
        Folder folder = folder(folderId, null, "Root");
        when(folderRepository.findByIdAndProjectId(folderId, project.getId()))
                .thenReturn(Optional.of(folder));

        UpdateFolderRequest selfParentRequest = new UpdateFolderRequest();
        selfParentRequest.setParentId(folderId);
        assertThatThrownBy(() -> service.updateFolder(
                project.getId(), folderId, selfParentRequest, owner))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.FOLDER_CYCLE_DETECTED);

        UUID childId = UUID.randomUUID();
        Folder child = folder(childId, folderId, "Child");
        when(folderRepository.findByIdAndProjectId(childId, project.getId()))
                .thenReturn(Optional.of(child));
        UpdateFolderRequest descendantRequest = new UpdateFolderRequest();
        descendantRequest.setName("Root");
        descendantRequest.setParentId(childId);
        assertThatThrownBy(() -> service.updateFolder(
                project.getId(), folderId, descendantRequest, owner))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.FOLDER_CYCLE_DETECTED);
    }

    @Test
    void deleteRequiresNoChildAndNoDocument() {
        Folder folder = folder(UUID.randomUUID(), null, "Docs");
        when(folderRepository.findByIdAndProjectId(folder.getId(), project.getId()))
                .thenReturn(Optional.of(folder));
        when(folderRepository.existsByParentIdAndProjectId(folder.getId(), project.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.deleteFolder(project.getId(), folder.getId(), owner))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.FOLDER_NOT_EMPTY);

        when(folderRepository.existsByParentIdAndProjectId(folder.getId(), project.getId()))
                .thenReturn(false);
        when(documentRepository.existsByFolderId(folder.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteFolder(project.getId(), folder.getId(), owner))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.FOLDER_NOT_EMPTY);

        when(documentRepository.existsByFolderId(folder.getId())).thenReturn(false);
        service.deleteFolder(project.getId(), folder.getId(), owner);
        verify(folderRepository).delete(folder);
    }

    private Folder folder(UUID id, UUID parentId, String name) {
        Folder folder = new Folder(project, null, name);
        folder.setId(id);
        folder.setParentId(parentId);
        return folder;
    }

    private static CustomUserPrincipal principal(SystemRole role) {
        return new CustomUserPrincipal(UUID.randomUUID(), UUID.randomUUID() + "@example.com",
                role, UserStatus.ACTIVE, true);
    }
}
