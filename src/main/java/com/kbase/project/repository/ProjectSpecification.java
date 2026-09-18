package com.kbase.project.repository;

import java.util.Locale;
import java.util.UUID;

import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;

import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

/** Admin project-listing filters: q over name/description, owner membership. */
public final class ProjectSpecification {

    private ProjectSpecification() {
    }

    public static Specification<Project> withFilters(String q, UUID ownerId) {
        Specification<Project> specification = (root, query, builder) -> builder.conjunction();
        if (q != null && !q.isBlank()) {
            String token = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("name")), token),
                    builder.like(builder.lower(root.get("description")), token)));
        }
        if (ownerId != null) {
            specification = specification.and((root, query, builder) -> {
                Subquery<UUID> subquery = query.subquery(UUID.class);
                var membership = subquery.from(ProjectMember.class);
                subquery.select(membership.get("project").get("id"))
                        .where(builder.equal(membership.get("user").get("id"), ownerId),
                                builder.equal(membership.get("role"), ProjectRole.OWNER));
                return root.get("id").in(subquery);
            });
        }
        return specification;
    }
}
