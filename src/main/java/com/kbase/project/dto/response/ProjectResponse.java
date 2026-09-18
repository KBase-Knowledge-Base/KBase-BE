package com.kbase.project.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.kbase.project.enums.ProjectRole;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Project representation for the current caller. {@code currentUserRole} is
 * null when an ADMIN accesses the project without membership; no fake role is
 * ever invented.
 */
public record ProjectResponse(
        UUID id,
        String name,
        String description,

        @JsonInclude(JsonInclude.Include.ALWAYS)
        ProjectRole currentUserRole,

        Instant createdAt,
        Instant updatedAt) {
}
