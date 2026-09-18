package com.kbase.document.repository;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.kbase.document.entity.Document;
import com.kbase.document.entity.DocumentTag;
import com.kbase.document.enums.FileKind;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

/**
 * Composable metadata filters for project-scoped document search.
 *
 * <p>The project predicate is deliberately mandatory in the aggregate search
 * factory. Tag matching uses an EXISTS subquery so one document is not
 * duplicated by a join against its junction rows.</p>
 */
public final class DocumentSpecification {

    private DocumentSpecification() {
    }

    /** Returns the mandatory project-isolation predicate. */
    public static Specification<Document> forProject(UUID projectId) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId is mandatory");
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("project").get("id"), requiredProjectId);
    }

    /** Alias that makes the required project filter explicit at call sites. */
    public static Specification<Document> byProjectId(UUID projectId) {
        return forProject(projectId);
    }

    /** Case-insensitive substring match over the approved metadata fields. */
    public static Specification<Document> withQuery(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return alwaysTrue();
        }
        String pattern = "%" + queryText.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> {
            Join<Object, Object> category = root.join("category", JoinType.LEFT);
            Predicate displayName = contains(criteriaBuilder, root.get("displayName"), pattern);
            Predicate originalFilename = contains(
                    criteriaBuilder, root.get("originalFilename"), pattern);
            Predicate description = contains(criteriaBuilder, root.get("description"), pattern);
            Predicate categoryName = contains(criteriaBuilder, category.get("name"), pattern);

            Subquery<UUID> tagMatch = query.subquery(UUID.class);
            Root<DocumentTag> documentTag = tagMatch.from(DocumentTag.class);
            Join<Object, Object> tag = documentTag.join("tag", JoinType.INNER);
            tagMatch.select(documentTag.get("id").get("documentId"))
                    .where(
                            criteriaBuilder.equal(
                                    documentTag.get("id").get("documentId"), root.get("id")),
                            contains(criteriaBuilder, tag.get("name"), pattern));

            return criteriaBuilder.or(
                    displayName,
                    originalFilename,
                    description,
                    categoryName,
                    criteriaBuilder.exists(tagMatch));
        };
    }

    /** Alias for the public search parameter name q. */
    public static Specification<Document> withQ(String queryText) {
        return withQuery(queryText);
    }

    public static Specification<Document> withFolderId(UUID folderId) {
        return folderId == null
                ? alwaysTrue()
                : (root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("folder").get("id"), folderId);
    }

    public static Specification<Document> withCategoryId(UUID categoryId) {
        return categoryId == null
                ? alwaysTrue()
                : (root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Document> withTagId(UUID tagId) {
        if (tagId == null) {
            return alwaysTrue();
        }
        return (root, query, criteriaBuilder) -> {
            Subquery<UUID> tagMatch = query.subquery(UUID.class);
            Root<DocumentTag> documentTag = tagMatch.from(DocumentTag.class);
            tagMatch.select(documentTag.get("id").get("documentId"))
                    .where(
                            criteriaBuilder.equal(
                                    documentTag.get("id").get("documentId"), root.get("id")),
                            criteriaBuilder.equal(
                                    documentTag.get("id").get("tagId"), tagId));
            return criteriaBuilder.exists(tagMatch);
        };
    }

    public static Specification<Document> withFileKind(FileKind fileKind) {
        return fileKind == null
                ? alwaysTrue()
                : (root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("fileKind"), fileKind);
    }

    public static Specification<Document> withUploadedBy(UUID userId) {
        return userId == null
                ? alwaysTrue()
                : (root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("uploadedBy").get("id"), userId);
    }

    public static Specification<Document> createdFrom(Instant createdFrom) {
        return createdFrom == null
                ? alwaysTrue()
                : (root, query, criteriaBuilder) ->
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("createdAt"), createdFrom);
    }

    public static Specification<Document> createdTo(Instant createdTo) {
        return createdTo == null
                ? alwaysTrue()
                : (root, query, criteriaBuilder) ->
                        criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdTo);
    }

    /**
     * Builds the complete M3 baseline. projectId is required; every other
     * filter is optional and corresponds to the Core v1 metadata contract.
     */
    public static Specification<Document> search(
            UUID projectId,
            String queryText,
            UUID folderId,
            UUID categoryId,
            UUID tagId,
            FileKind fileKind,
            UUID uploadedBy,
            Instant createdFrom,
            Instant createdTo) {
        return forProject(projectId)
                .and(withQuery(queryText))
                .and(withFolderId(folderId))
                .and(withCategoryId(categoryId))
                .and(withTagId(tagId))
                .and(withFileKind(fileKind))
                .and(withUploadedBy(uploadedBy))
                .and(createdFrom(createdFrom))
                .and(createdTo(createdTo));
    }

    private static Predicate contains(
            CriteriaBuilder criteriaBuilder,
            jakarta.persistence.criteria.Expression<String> expression,
            String pattern) {
        return criteriaBuilder.like(criteriaBuilder.lower(expression), pattern);
    }

    private static Specification<Document> alwaysTrue() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
    }
}
