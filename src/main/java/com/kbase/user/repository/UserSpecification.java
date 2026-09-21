package com.kbase.user.repository;

import java.util.Locale;

import com.kbase.shared.util.LikePatterns;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import org.springframework.data.jpa.domain.Specification;

/** Admin user-list filters: q over email/displayName, status and system role. */
public final class UserSpecification {

    private UserSpecification() {
    }

    public static Specification<User> withFilters(String q, UserStatus status, SystemRole systemRole) {
        Specification<User> specification = (root, query, builder) -> builder.conjunction();
        if (q != null && !q.isBlank()) {
            String token = "%" + LikePatterns.escape(q.trim().toLowerCase(Locale.ROOT)) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("email")), token, LikePatterns.ESCAPE),
                    builder.like(builder.lower(root.get("displayName")), token, LikePatterns.ESCAPE)));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("status"), status));
        }
        if (systemRole != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("systemRole"), systemRole));
        }
        return specification;
    }
}
