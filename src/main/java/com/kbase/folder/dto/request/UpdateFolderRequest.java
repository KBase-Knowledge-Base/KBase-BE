package com.kbase.folder.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Size;

/**
 * Partial folder update. Setter presence flags distinguish an omitted
 * parentId from an explicit null, which is required when moving a folder to
 * the root level.
 */
public final class UpdateFolderRequest {

    @Size(max = 150, message = "Folder name must contain at most 150 characters")
    private String name;
    private UUID parentId;
    private boolean nameProvided;
    private boolean parentIdProvided;

    public UpdateFolderRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
        this.parentIdProvided = true;
    }

    public String name() {
        return name;
    }

    public UUID parentId() {
        return parentId;
    }

    public boolean nameProvided() {
        return nameProvided;
    }

    public boolean parentIdProvided() {
        return parentIdProvided;
    }
}
