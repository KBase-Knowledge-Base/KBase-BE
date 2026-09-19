package com.kbase.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.kbase.document.dto.request.DocumentMetadataRequest;
import com.kbase.document.dto.request.UpdateDocumentRequest;
import com.kbase.document.dto.response.BatchDocumentUploadResponse;
import com.kbase.document.dto.response.DocumentResponse;
import com.kbase.document.entity.Document;
import com.kbase.document.entity.DocumentTag;
import com.kbase.document.mapper.DocumentMapper;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.repository.DocumentTagRepository;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.exception.InfrastructureException;
import com.kbase.storage.exception.StorageException;
import com.kbase.storage.exception.StorageUnavailableException;
import com.kbase.storage.model.StorageUploadRequest;
import com.kbase.storage.model.StoredResource;
import com.kbase.storage.service.StorageKeyFactory;
import com.kbase.storage.service.StorageService;
import com.kbase.user.entity.User;
import com.kbase.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Owns document orchestration; MinIO is reachable only through StorageService. */
@Service
public class DocumentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentService.class);
    private static final Collection<String> PREVIEW_EXTENSIONS = List.of(
            "pdf", "jpg", "jpeg", "png", "gif", "svg", "bmp", "txt", "md", "mp4");

    private final DocumentRepository documentRepository;
    private final DocumentTagRepository documentTagRepository;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final DocumentAuthorizationService documentAuthorizationService;
    private final DocumentMetadataResolver metadataResolver;
    private final FileValidationService fileValidationService;
    private final StorageService storageService;
    private final StorageKeyFactory storageKeyFactory;
    private final DocumentMapper documentMapper;
    private final UserRepository userRepository;

    public DocumentService(DocumentRepository documentRepository, DocumentTagRepository documentTagRepository,
            ProjectAuthorizationService projectAuthorizationService,
            DocumentAuthorizationService documentAuthorizationService,
            DocumentMetadataResolver metadataResolver, FileValidationService fileValidationService,
            StorageService storageService, StorageKeyFactory storageKeyFactory,
            DocumentMapper documentMapper, UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.documentTagRepository = documentTagRepository;
        this.projectAuthorizationService = projectAuthorizationService;
        this.documentAuthorizationService = documentAuthorizationService;
        this.metadataResolver = metadataResolver;
        this.fileValidationService = fileValidationService;
        this.storageService = storageService;
        this.storageKeyFactory = storageKeyFactory;
        this.documentMapper = documentMapper;
        this.userRepository = userRepository;
    }

    @Transactional
    public DocumentResponse upload(UUID projectId, MultipartFile file, DocumentMetadataRequest metadata,
            CustomUserPrincipal principal) {
        return uploadInternal(projectId, file, metadata, principal);
    }

    @Transactional
    public BatchDocumentUploadResponse batchUpload(UUID projectId, List<MultipartFile> files,
            DocumentMetadataRequest metadata, CustomUserPrincipal principal) {
        projectAuthorizationService.requireProjectAccess(projectId, principal);
        fileValidationService.validateBatchCount(files == null ? 0 : files.size());
        // Validate every file before creating any external object.
        for (MultipartFile file : files) { fileValidationService.validate(file); }
        List<DocumentResponse> saved = new ArrayList<>();
        List<String> uploadedKeys = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                DocumentResponse response = uploadInternal(projectId, file, metadata, principal, uploadedKeys);
                saved.add(response);
            }
            return new BatchDocumentUploadResponse(List.copyOf(saved));
        } catch (RuntimeException exception) {
            cleanupUploaded(projectId, null, uploadedKeys, exception);
            throw exception;
        }
    }

    private DocumentResponse uploadInternal(UUID projectId, MultipartFile file, DocumentMetadataRequest metadata,
            CustomUserPrincipal principal) {
        return uploadInternal(projectId, file, metadata, principal, new ArrayList<>());
    }

    private DocumentResponse uploadInternal(UUID projectId, MultipartFile file, DocumentMetadataRequest metadata,
            CustomUserPrincipal principal, List<String> uploadedKeys) {
        var access = projectAuthorizationService.requireProjectAccess(projectId, principal);
        ValidatedFile validated = fileValidationService.validate(file);
        DocumentMetadataRequest safeMetadata = metadata == null
                ? new DocumentMetadataRequest(null, null, null, null, List.of()) : metadata;
        ResolvedDocumentMetadata resolved = metadataResolver.resolve(projectId, safeMetadata.folderId(),
                safeMetadata.categoryId(), safeMetadata.tagIds());
        UUID documentId = UUID.randomUUID();
        String storageKey = storageKeyFactory.documentObjectKey(projectId, documentId, validated.extension());
        try (InputStream input = file.getInputStream()) {
            storageService.upload(new StorageUploadRequest(storageKey, input, validated.sizeBytes(), validated.mimeType()));
            uploadedKeys.add(storageKey);
        } catch (StorageException exception) {
            throw storageFailureForUpload(exception);
        } catch (IOException exception) {
            throw new InfrastructureException(ErrorCode.FILE_UPLOAD_FAILED, exception);
        }
        try {
            User uploader = userRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            String displayName = safeDisplayName(safeMetadata.displayName(), validated.originalFilename());
            Document document = new Document(access.project(), uploader, displayName, validated.originalFilename(),
                    validated.fileKind(), validated.extension(), validated.mimeType(), validated.sizeBytes(), storageKey);
            document.setId(documentId);
            document.setDescription(safeMetadata.description());
            document.setFolder(resolved.folder());
            document.setCategory(resolved.category());
            // A backend-generated UUID makes Spring Data choose merge for a
            // new entity. Keep the returned managed instance so lifecycle
            // timestamps and subsequent tag associations are reflected in the
            // response and transaction.
            document = documentRepository.saveAndFlush(document);
            persistTags(document, resolved.tags());
            return documentMapper.toResponse(document, documentTagRepository.findAllByIdDocumentId(documentId));
        } catch (RuntimeException exception) {
            cleanupUploaded(projectId, documentId, List.of(storageKey), exception);
            if (exception instanceof BusinessException) { throw exception; }
            throw new InfrastructureException(ErrorCode.FILE_UPLOAD_FAILED, exception);
        }
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId, CustomUserPrincipal principal) {
        Document document = requireDetail(documentId);
        documentAuthorizationService.requireReadPermission(document, principal);
        return documentMapper.toResponse(document, documentTagRepository.findAllByIdDocumentId(documentId));
    }

    @Transactional
    public DocumentResponse updateMetadata(UUID documentId, UpdateDocumentRequest request,
            CustomUserPrincipal principal) {
        Objects.requireNonNull(request, "request");
        Document document = requireDetail(documentId);
        documentAuthorizationService.requireModifyPermission(document, principal);
        ResolvedDocumentMetadata resolved = metadataResolver.resolve(document.getProject().getId(),
                request.folderId() == null ? document.getFolderId() : request.folderId(),
                request.categoryId() == null ? document.getCategoryId() : request.categoryId(),
                request.tagIds() == null ? documentTagRepository.findAllByIdDocumentId(documentId).stream()
                        .map(relation -> relation.getTag().getId()).toList() : request.tagIds());
        if (request.displayName() != null) {
            document.setDisplayName(safeDisplayName(request.displayName(), document.getOriginalFilename()));
        }
        if (request.description() != null) { document.setDescription(request.description()); }
        document.setFolder(resolved.folder());
        document.setCategory(resolved.category());
        documentTagRepository.deleteAllByIdDocumentId(documentId);
        documentTagRepository.flush();
        persistTags(document, resolved.tags());
        // storageKey intentionally remains untouched for rename/move/category/tag changes.
        return documentMapper.toResponse(document, documentTagRepository.findAllByIdDocumentId(documentId));
    }

    @Transactional(readOnly = true)
    public FileDelivery download(UUID documentId, CustomUserPrincipal principal) {
        Document document = requireDetail(documentId);
        documentAuthorizationService.requireReadPermission(document, principal);
        return delivery(document, false, null);
    }

    @Transactional(readOnly = true)
    public FileDelivery preview(UUID documentId, CustomUserPrincipal principal, String rangeHeader) {
        Document document = requireDetail(documentId);
        documentAuthorizationService.requireReadPermission(document, principal);
        if (!PREVIEW_EXTENSIONS.contains(document.getExtension())) {
            throw new BusinessException(ErrorCode.PREVIEW_NOT_SUPPORTED);
        }
        return delivery(document, true, rangeHeader);
    }

    @Transactional
    public void delete(UUID documentId, CustomUserPrincipal principal) {
        Document document = requireDetail(documentId);
        documentAuthorizationService.requireModifyPermission(document, principal);
        try {
            storageService.delete(document.getStorageKey());
        } catch (StorageException exception) {
            throw storageFailureForDelete(exception, ErrorCode.DOCUMENT_DELETE_FAILED);
        }
        try {
            documentRepository.delete(document); // schema cascade removes DocumentTag after storage success.
            documentRepository.flush();
        } catch (RuntimeException exception) {
            LOGGER.error("Document DB delete failed after storage deletion documentId={} projectId={}",
                    documentId, document.getProject().getId(), exception);
            throw new InfrastructureException(ErrorCode.DOCUMENT_DELETE_FAILED, exception);
        }
    }

    private FileDelivery delivery(Document document, boolean preview, String rangeHeader) {
        try {
            if (preview && "mp4".equals(document.getExtension()) && rangeHeader != null) {
                long total = storageService.stat(document.getStorageKey()).sizeBytes();
                long[] range = parseRange(rangeHeader, total);
                StoredResource resource = storageService.getRange(document.getStorageKey(), range[0], range[1] - range[0] + 1);
                return new FileDelivery(resource.inputStream(), resource.contentLength(), total, document.getMimeType(),
                        document.getDisplayName(), true, range[0], range[1]);
            }
            StoredResource resource = storageService.get(document.getStorageKey());
            return new FileDelivery(resource.inputStream(), resource.contentLength(), resource.contentLength(),
                    document.getMimeType(), document.getDisplayName(), false, 0, resource.contentLength() - 1);
        } catch (StorageException exception) {
            if (exception instanceof StorageUnavailableException) {
                throw new InfrastructureException(ErrorCode.STORAGE_SERVICE_UNAVAILABLE, exception);
            }
            LOGGER.error("Storage read failed documentId={} projectId={}", document.getId(),
                    document.getProject().getId(), exception);
            throw new InfrastructureException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private static long[] parseRange(String header, long total) {
        if (total <= 0 || !header.startsWith("bytes=") || header.contains(",")) throw new InvalidRangeException(total);
        try {
            String[] parts = header.substring(6).split("-", -1);
            if (parts.length != 2 || parts[0].isBlank()) throw new InvalidRangeException(total);
            long start = Long.parseLong(parts[0]);
            long end = parts[1].isBlank() ? total - 1 : Long.parseLong(parts[1]);
            if (start < 0 || end < start || start >= total) throw new InvalidRangeException(total);
            return new long[] {start, Math.min(end, total - 1)};
        } catch (NumberFormatException exception) { throw new InvalidRangeException(total); }
    }

    private Document requireDetail(UUID documentId) {
        return documentRepository.findDetailById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private void persistTags(Document document, List<com.kbase.tag.entity.Tag> tags) {
        // Retain both object references as well as the composite key. The
        // response mapper needs the tag projection in this transaction, while
        // the database composite foreign keys remain the final integrity check.
        List<DocumentTag> relations = tags.stream().map(tag -> new DocumentTag(
                document, tag, document.getProject().getId())).toList();
        documentTagRepository.saveAll(relations);
        documentTagRepository.flush();
    }

    private static String safeDisplayName(String requested, String fallback) {
        String name = requested == null || requested.isBlank() ? fallback : requested.trim();
        if (name.length() > 255) throw new BusinessException(ErrorCode.INVALID_FILE_METADATA);
        return name;
    }

    private void cleanupUploaded(UUID projectId, UUID documentId, Collection<String> keys, RuntimeException root) {
        for (String key : keys) {
            try { storageService.delete(key); }
            catch (RuntimeException cleanupFailure) {
                LOGGER.error("COMPENSATION FAILURE projectId={} documentId={} storageKey={} rootFailure={}",
                        projectId, documentId, key, root.getClass().getSimpleName(), cleanupFailure);
            }
        }
    }

    private static InfrastructureException storageFailureForUpload(StorageException exception) {
        return new InfrastructureException(exception instanceof StorageUnavailableException
                ? ErrorCode.STORAGE_SERVICE_UNAVAILABLE : ErrorCode.FILE_UPLOAD_FAILED, exception);
    }

    private static InfrastructureException storageFailureForDelete(StorageException exception, ErrorCode fallback) {
        return new InfrastructureException(exception instanceof StorageUnavailableException
                ? ErrorCode.STORAGE_SERVICE_UNAVAILABLE : fallback, exception);
    }
}
