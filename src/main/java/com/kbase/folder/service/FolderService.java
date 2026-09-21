package com.kbase.folder.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.kbase.document.repository.DocumentRepository;
import com.kbase.folder.dto.request.CreateFolderRequest;
import com.kbase.folder.dto.request.UpdateFolderRequest;
import com.kbase.folder.dto.response.FolderResponse;
import com.kbase.folder.entity.Folder;
import com.kbase.folder.mapper.FolderMapper;
import com.kbase.folder.repository.FolderRepository;
import com.kbase.project.entity.Project;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;

import jakarta.validation.Valid;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project-scoped folder hierarchy and OWNER/ADMIN management rules. */
@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final ProjectAuthorizationService authorizationService;
    private final FolderMapper folderMapper;

    public FolderService(
            FolderRepository folderRepository,
            DocumentRepository documentRepository,
            ProjectAuthorizationService authorizationService,
            FolderMapper folderMapper) {
        this.folderRepository = Objects.requireNonNull(folderRepository, "folderRepository");
        this.documentRepository = Objects.requireNonNull(documentRepository, "documentRepository");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.folderMapper = Objects.requireNonNull(folderMapper, "folderMapper");
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> listFolders(
            UUID projectId, UUID parentId, CustomUserPrincipal principal) {
        authorizationService.requireProjectAccess(projectId, principal);
        if (parentId != null) {
            requireParent(projectId, parentId);
            return folderRepository.findAllByProjectIdAndParentId(projectId, parentId)
                    .stream().map(folderMapper::toResponse).toList();
        }
        return folderRepository.findAllByProjectId(projectId)
                .stream().map(folderMapper::toResponse).toList();
    }

    @Transactional
    public FolderResponse createFolder(
            UUID projectId, @Valid CreateFolderRequest request, CustomUserPrincipal principal) {
        Objects.requireNonNull(request, "request");
        ProjectAuthorizationService.ProjectAccess access =
                authorizationService.requireOwner(projectId, principal);
        Folder parent = request.parentId() == null
                ? null
                : requireParent(projectId, request.parentId());
        String name = normalizeName(request.name(), "Folder name");
        ensureUnique(projectId, parent == null ? null : parent.getId(), name, null);

        Folder folder = folderRepository.save(new Folder(access.project(), parent, name));
        return folderMapper.toResponse(folder);
    }

    @Transactional
    public FolderResponse updateFolder(
            UUID projectId,
            UUID folderId,
            @Valid UpdateFolderRequest request,
            CustomUserPrincipal principal) {
        Objects.requireNonNull(request, "request");
        authorizationService.requireOwner(projectId, principal);
        if (request.parentIdProvided() && request.parentId() != null) {
            // Both rows are locked (deterministic order) before any cycle
            // check reads parent state, so two opposite concurrent moves
            // serialize instead of interleaving into a cycle.
            lockForMove(projectId, folderId, request.parentId());
        }
        Folder folder = folderRepository.findByIdAndProjectId(folderId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));

        String name = folder.getName();
        if (request.nameProvided()) {
            name = normalizeName(request.name(), "Folder name");
        }

        UUID parentId = folder.getParentId();
        Folder parent = null;
        if (request.parentIdProvided()) {
            parentId = request.parentId();
            parent = parentId == null ? null : requireParent(projectId, parentId);
            if (parentId != null) {
                ensureNoCycle(projectId, folder, parent);
            }
        }

        if (request.nameProvided() || request.parentIdProvided()) {
            ensureUnique(projectId, parentId, name, folderId);
            folder.setName(name);
            if (request.parentIdProvided()) {
                folder.setParent(parent);
            }
        }
        return folderMapper.toResponse(folder);
    }

    /**
     * Locks the moved folder and its target parent in ascending-UUID order so
     * the two opposite moves take the locks in the same sequence and can never
     * deadlock; the loser then re-reads the committed graph and fails the
     * cycle walk.
     */
    private void lockForMove(UUID projectId, UUID folderId, UUID newParentId) {
        List<UUID> orderedIds = java.util.stream.Stream.of(folderId, newParentId)
                .distinct()
                .sorted()
                .toList();
        for (UUID lockedId : orderedIds) {
            folderRepository.findByIdForUpdate(lockedId, projectId)
                    .orElseThrow(() -> new BusinessException(lockedId.equals(folderId)
                            ? ErrorCode.FOLDER_NOT_FOUND
                            : ErrorCode.PARENT_FOLDER_NOT_FOUND));
        }
    }

    @Transactional
    public void deleteFolder(UUID projectId, UUID folderId, CustomUserPrincipal principal) {
        authorizationService.requireOwner(projectId, principal);
        Folder folder = folderRepository.findByIdAndProjectId(folderId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));
        if (folderRepository.existsByParentIdAndProjectId(folder.getId(), projectId)
                || documentRepository.existsByFolderId(folder.getId())) {
            throw new BusinessException(ErrorCode.FOLDER_NOT_EMPTY);
        }
        folderRepository.delete(folder);
    }

    private Folder requireParent(UUID projectId, UUID parentId) {
        return folderRepository.findByIdAndProjectId(parentId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARENT_FOLDER_NOT_FOUND));
    }

    private void ensureUnique(UUID projectId, UUID parentId, String name, UUID excludedFolderId) {
        boolean duplicate;
        if (excludedFolderId == null) {
            duplicate = parentId == null
                    ? folderRepository.existsByProjectIdAndParentIdIsNullAndNameIgnoreCase(projectId, name)
                    : folderRepository.existsByProjectIdAndParentIdAndNameIgnoreCase(projectId, parentId, name);
        } else {
            duplicate = parentId == null
                    ? folderRepository.existsByProjectIdAndParentIdIsNullAndNameIgnoreCaseAndIdNot(
                            projectId, name, excludedFolderId)
                    : folderRepository.existsByProjectIdAndParentIdAndNameIgnoreCaseAndIdNot(
                            projectId, parentId, name, excludedFolderId);
        }
        if (duplicate) {
            throw new BusinessException(ErrorCode.FOLDER_NAME_ALREADY_EXISTS);
        }
    }

    /** Walks the proposed parent's ancestors and rejects self/descendant moves. */
    private void ensureNoCycle(UUID projectId, Folder folder, Folder newParent) {
        UUID currentId = newParent.getId();
        Set<UUID> visited = new HashSet<>();
        while (currentId != null) {
            if (folder.getId().equals(currentId) || !visited.add(currentId)) {
                throw new BusinessException(ErrorCode.FOLDER_CYCLE_DETECTED);
            }
            Folder current = folderRepository.findByIdAndProjectId(currentId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARENT_FOLDER_NOT_FOUND));
            currentId = current.getParentId();
        }
    }

    private static String normalizeName(String value, String label) {
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + " is required.");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + " is required.");
        }
        return normalized;
    }
}
