package com.kbase.document.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import com.kbase.category.entity.Category;
import com.kbase.category.repository.CategoryRepository;
import com.kbase.folder.entity.Folder;
import com.kbase.folder.repository.FolderRepository;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.tag.entity.Tag;
import com.kbase.tag.repository.TagRepository;

import org.springframework.stereotype.Component;

/** Resolves only resources owned by the target project; no cross-project IDs leak through. */
@Component
class DocumentMetadataResolver {
    private final FolderRepository folderRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    DocumentMetadataResolver(FolderRepository folderRepository, CategoryRepository categoryRepository,
            TagRepository tagRepository) {
        this.folderRepository = folderRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
    }

    ResolvedDocumentMetadata resolve(UUID projectId, UUID folderId, UUID categoryId, List<UUID> tagIds) {
        Folder folder = folderId == null ? null : folderRepository.findByIdAndProjectId(folderId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));
        Category category = categoryId == null ? null
                : categoryRepository.findByIdAndProjectId(categoryId, projectId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        List<UUID> requested = tagIds == null ? List.of() : tagIds;
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(requested);
        if (unique.size() != requested.size()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_METADATA, "Duplicate tags are not allowed.");
        }
        List<Tag> tags = tagRepository.findAllByProjectIdAndIdIn(projectId, unique);
        if (tags.size() != unique.size()) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }
        return new ResolvedDocumentMetadata(folder, category, tags);
    }
}
