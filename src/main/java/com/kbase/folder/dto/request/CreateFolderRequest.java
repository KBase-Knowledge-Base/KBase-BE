package com.kbase.folder.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request for a project folder at root level or below an existing parent. */
public record CreateFolderRequest(
        @NotBlank(message = "Folder name is required")
        @Size(max = 150, message = "Folder name must contain at most 150 characters")
        String name,
        UUID parentId) {
}
