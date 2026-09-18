package com.kbase.tag.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.kbase.project.enums.ProjectRole;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.tag.dto.request.CreateTagRequest;
import com.kbase.tag.dto.request.UpdateTagRequest;
import com.kbase.tag.dto.response.TagResponse;
import com.kbase.tag.entity.Tag;
import com.kbase.tag.mapper.TagMapper;
import com.kbase.tag.repository.TagRepository;

import jakarta.validation.Valid;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project-scoped tags. MEMBER can create; only OWNER/ADMIN can mutate shared tags. */
@Service
public class TagService {

    private final TagRepository tagRepository;
    private final ProjectAuthorizationService authorizationService;
    private final TagMapper tagMapper;

    public TagService(
            TagRepository tagRepository,
            ProjectAuthorizationService authorizationService,
            TagMapper tagMapper) {
        this.tagRepository = Objects.requireNonNull(tagRepository, "tagRepository");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.tagMapper = Objects.requireNonNull(tagMapper, "tagMapper");
    }

    @Transactional(readOnly = true)
    public List<TagResponse> listTags(
            UUID projectId, String query, CustomUserPrincipal principal) {
        authorizationService.requireProjectAccess(projectId, principal);
        String normalizedQuery = query == null ? "" : query.trim();
        List<Tag> tags = normalizedQuery.isBlank()
                ? tagRepository.findAllByProjectIdOrderByNameAsc(projectId)
                : tagRepository.findAllByProjectIdAndNameContainingIgnoreCaseOrderByNameAsc(
                        projectId, normalizedQuery);
        return tags.stream().map(tagMapper::toResponse).toList();
    }

    @Transactional
    public TagResponse createTag(
            UUID projectId, @Valid CreateTagRequest request, CustomUserPrincipal principal) {
        Objects.requireNonNull(request, "request");
        ProjectAuthorizationService.ProjectAccess access =
                authorizationService.requireProjectAccess(projectId, principal);
        String name = normalizeName(request.name());
        ensureUnique(projectId, name, null);
        return tagMapper.toResponse(tagRepository.save(new Tag(access.project(), name)));
    }

    @Transactional
    public TagResponse renameTag(
            UUID projectId,
            UUID tagId,
            @Valid UpdateTagRequest request,
            CustomUserPrincipal principal) {
        Objects.requireNonNull(request, "request");
        requireTagManagement(projectId, principal);
        Tag tag = tagRepository.findByIdAndProjectId(tagId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
        String name = normalizeName(request.name());
        ensureUnique(projectId, name, tagId);
        tag.setName(name);
        return tagMapper.toResponse(tag);
    }

    @Transactional
    public void deleteTag(UUID projectId, UUID tagId, CustomUserPrincipal principal) {
        requireTagManagement(projectId, principal);
        Tag tag = tagRepository.findByIdAndProjectId(tagId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
        // PostgreSQL's FK ON DELETE CASCADE removes DocumentTag rows only;
        // Document rows are not cascaded from Tag.
        tagRepository.delete(tag);
    }

    private void requireTagManagement(UUID projectId, CustomUserPrincipal principal) {
        ProjectAuthorizationService.ProjectAccess access =
                authorizationService.requireProjectAccess(projectId, principal);
        if (!access.adminOverride() && access.role() != ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.TAG_MANAGEMENT_FORBIDDEN);
        }
    }

    private void ensureUnique(UUID projectId, String name, UUID excludedTagId) {
        boolean duplicate = excludedTagId == null
                ? tagRepository.existsByProjectIdAndNameIgnoreCase(projectId, name)
                : tagRepository.existsByProjectIdAndNameIgnoreCaseAndIdNot(
                        projectId, name, excludedTagId);
        if (duplicate) {
            throw new BusinessException(ErrorCode.TAG_NAME_ALREADY_EXISTS);
        }
    }

    private static String normalizeName(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tag name is required.");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tag name is required.");
        }
        return normalized;
    }
}
