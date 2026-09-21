package com.kbase.document.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.io.ByteArrayInputStream;

import com.kbase.document.dto.request.DocumentMetadataRequest;
import com.kbase.document.entity.Document;
import com.kbase.document.enums.FileKind;
import com.kbase.document.mapper.DocumentMapper;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.repository.DocumentTagRepository;
import com.kbase.project.entity.Project;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.project.service.ProjectAuthorizationService.ProjectAccess;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.KBaseException;
import com.kbase.storage.service.StorageKeyFactory;
import com.kbase.storage.service.StorageService;
import com.kbase.storage.exception.StorageDeleteException;
import com.kbase.storage.model.ObjectMetadata;
import com.kbase.storage.model.StoredResource;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentServiceTest {
    private DocumentRepository documents;
    private DocumentTagRepository documentTags;
    private ProjectAuthorizationService projectAuthorization;
    private DocumentAuthorizationService documentAuthorization;
    private DocumentMetadataResolver metadataResolver;
    private FileValidationService validation;
    private StorageService storage;
    private UserRepository users;
    private DocumentService service;
    private Project project;
    private User user;
    private CustomUserPrincipal principal;

    @BeforeEach
    void setUp() {
        documents = mock(DocumentRepository.class);
        documentTags = mock(DocumentTagRepository.class);
        projectAuthorization = mock(ProjectAuthorizationService.class);
        documentAuthorization = mock(DocumentAuthorizationService.class);
        metadataResolver = mock(DocumentMetadataResolver.class);
        validation = mock(FileValidationService.class);
        storage = mock(StorageService.class);
        users = mock(UserRepository.class);
        service = new DocumentService(documents, documentTags, projectAuthorization, documentAuthorization,
                metadataResolver, validation, storage, new StorageKeyFactory(), new DocumentMapper(), users);
        project = new Project("P", null); project.setId(UUID.randomUUID());
        user = new User("member@example.com", "hash", "Member", SystemRole.USER, UserStatus.ACTIVE);
        user.setId(UUID.randomUUID());
        principal = new CustomUserPrincipal(user.getId(), user.getEmail(), SystemRole.USER, UserStatus.ACTIVE, true);
    }

    @Test
    void persistenceFailureAfterUploadCompensatesStorageWithoutLeakingKey() {
        MockMultipartFile file = new MockMultipartFile("file", "one.pdf", "application/pdf", "%PDF".getBytes());
        when(projectAuthorization.requireProjectAccess(project.getId(), principal))
                .thenReturn(new ProjectAccess(project, ProjectRole.MEMBER, false));
        when(validation.validate(file)).thenReturn(new ValidatedFile("one.pdf", "pdf", "application/pdf",
                FileKind.DOCUMENT, file.getSize()));
        when(metadataResolver.resolve(project.getId(), null, null, List.of()))
                .thenReturn(new ResolvedDocumentMetadata(null, null, List.of()));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(documents.saveAndFlush(any(Document.class))).thenThrow(new RuntimeException("db failure"));

        assertThatThrownBy(() -> service.upload(project.getId(), file,
                new DocumentMetadataRequest(null, null, null, null, List.of()), principal))
                .isInstanceOf(KBaseException.class)
                .extracting(error -> ((KBaseException) error).getErrorCode().code()).isEqualTo("FILE_UPLOAD_FAILED");
        var order = inOrder(storage, documents);
        order.verify(storage).upload(any());
        order.verify(documents).saveAndFlush(any());
        verify(storage).delete(org.mockito.ArgumentMatchers.matches("projects/.+/documents/.+\\.pdf"));
    }

    @Test
    void hardDeleteIsAuthorizedStorageFirstThenDatabase() {
        Document document = new Document(project, user, "one.pdf", "one.pdf", FileKind.DOCUMENT,
                "pdf", "application/pdf", 4, "projects/a/documents/one.pdf");
        document.setId(UUID.randomUUID());
        when(documents.findDetailById(document.getId())).thenReturn(Optional.of(document));

        service.delete(document.getId(), principal);

        var order = inOrder(documentAuthorization, storage, documents);
        order.verify(documentAuthorization).requireModifyPermission(document, principal);
        order.verify(storage).delete(document.getStorageKey());
        order.verify(documents).delete(document);
        order.verify(documents).flush();
    }

    @Test
    void storageDeleteFailureLeavesDocumentMetadataUntouched() {
        Document document = new Document(project, user, "one.pdf", "one.pdf", FileKind.DOCUMENT,
                "pdf", "application/pdf", 4, "projects/a/documents/one.pdf");
        document.setId(UUID.randomUUID());
        when(documents.findDetailById(document.getId())).thenReturn(Optional.of(document));
        org.mockito.Mockito.doThrow(new StorageDeleteException("delete failed", new RuntimeException()))
                .when(storage).delete(document.getStorageKey());

        assertThatThrownBy(() -> service.delete(document.getId(), principal))
                .isInstanceOf(KBaseException.class)
                .extracting(error -> ((KBaseException) error).getErrorCode().code())
                .isEqualTo("DOCUMENT_DELETE_FAILED");
        verify(documents, never()).delete(any(Document.class));
    }

    @Test
    void compensationFailureStillReturnsSafeUploadError() {
        MockMultipartFile file = new MockMultipartFile("file", "one.pdf", "application/pdf", "%PDF".getBytes());
        when(projectAuthorization.requireProjectAccess(project.getId(), principal))
                .thenReturn(new ProjectAccess(project, ProjectRole.MEMBER, false));
        when(validation.validate(file)).thenReturn(new ValidatedFile("one.pdf", "pdf", "application/pdf",
                FileKind.DOCUMENT, file.getSize()));
        when(metadataResolver.resolve(project.getId(), null, null, List.of()))
                .thenReturn(new ResolvedDocumentMetadata(null, null, List.of()));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(documents.saveAndFlush(any(Document.class))).thenThrow(new RuntimeException("db failure"));
        org.mockito.Mockito.doThrow(new StorageDeleteException("cleanup failed", new RuntimeException()))
                .when(storage).delete(org.mockito.ArgumentMatchers.anyString());

        assertThatThrownBy(() -> service.upload(project.getId(), file,
                new DocumentMetadataRequest(null, null, null, null, List.of()), principal))
                .isInstanceOf(KBaseException.class)
                .satisfies(error -> {
                    KBaseException kbase = (KBaseException) error;
                    org.assertj.core.api.Assertions.assertThat(kbase.getErrorCode().code()).isEqualTo("FILE_UPLOAD_FAILED");
                    org.assertj.core.api.Assertions.assertThat(kbase.getMessage()).doesNotContain("projects/");
                });
        verify(storage).delete(org.mockito.ArgumentMatchers.matches("projects/.+/documents/.+\\.pdf"));
    }

    @Test
    void batchUploadCompensatesEachUploadedKeyExactlyOnce() {
        MockMultipartFile first = new MockMultipartFile("files", "one.pdf", "application/pdf", "%PDF".getBytes());
        MockMultipartFile second = new MockMultipartFile("files", "two.pdf", "application/pdf", "%PDF".getBytes());
        when(projectAuthorization.requireProjectAccess(project.getId(), principal))
                .thenReturn(new ProjectAccess(project, ProjectRole.MEMBER, false));
        when(validation.validate(first)).thenReturn(new ValidatedFile("one.pdf", "pdf", "application/pdf",
                FileKind.DOCUMENT, first.getSize()));
        when(validation.validate(second)).thenReturn(new ValidatedFile("two.pdf", "pdf", "application/pdf",
                FileKind.DOCUMENT, second.getSize()));
        when(metadataResolver.resolve(org.mockito.ArgumentMatchers.eq(project.getId()),
                any(), any(), any()))
                .thenReturn(new ResolvedDocumentMetadata(null, null, List.of()));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(documents.saveAndFlush(any(Document.class)))
                .thenAnswer(invocation -> {
                    Document saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                })
                .thenThrow(new RuntimeException("db failure"));

        assertThatThrownBy(() -> service.batchUpload(project.getId(), List.of(first, second),
                new DocumentMetadataRequest(null, null, null, null, List.of()), principal))
                .isInstanceOf(KBaseException.class)
                .extracting(error -> ((KBaseException) error).getErrorCode().code())
                .isEqualTo("FILE_UPLOAD_FAILED");

        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage, org.mockito.Mockito.times(2)).delete(keys.capture());
        org.assertj.core.api.Assertions.assertThat(keys.getAllValues()).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void mp4PreviewUsesSingleRangeAndRejectsInvalidRangesWithoutWholeFileRead() {
        Document video = new Document(project, user, "clip.mp4", "clip.mp4", FileKind.VIDEO,
                "mp4", "video/mp4", 10, "projects/a/documents/clip.mp4");
        video.setId(UUID.randomUUID());
        when(documents.findDetailById(video.getId())).thenReturn(Optional.of(video));
        when(storage.stat(video.getStorageKey())).thenReturn(new ObjectMetadata(video.getStorageKey(), 10,
                "video/mp4", "etag", Instant.now()));
        when(storage.getRange(video.getStorageKey(), 2, 3)).thenReturn(new StoredResource(
                new ByteArrayInputStream(new byte[] {2, 3, 4}), 3, "video/mp4"));

        FileDelivery result = service.preview(video.getId(), principal, "bytes=2-4");
        org.assertj.core.api.Assertions.assertThat(result.partial()).isTrue();
        org.assertj.core.api.Assertions.assertThat(result.rangeStart()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(result.rangeEnd()).isEqualTo(4);
        verify(storage).getRange(video.getStorageKey(), 2, 3);
        assertThatThrownBy(() -> service.preview(video.getId(), principal, "bytes=10-11"))
                .isInstanceOf(InvalidRangeException.class);
    }
}
