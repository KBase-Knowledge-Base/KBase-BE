package com.kbase.project.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.kbase.project.enums.ProjectRole;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Project representation for the current caller. {@code currentUserRole} is
 * null when an ADMIN accesses the project without membership; no fake role is
 * ever invented.
 */
public record ProjectResponse(
        UUID id,
        String name,

        @Schema(nullable = true)
        String description,

        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(nullable = true, description = "Project role of the caller; null when an ADMIN views the project "
                + "without membership. OWNER and MEMBER are the only project roles.")
        ProjectRole currentUserRole,

        Instant createdAt,
        Instant updatedAt) {
}
