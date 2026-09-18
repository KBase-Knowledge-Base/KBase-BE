package com.kbase.folder.mapper;

import java.util.Objects;

import com.kbase.folder.dto.response.FolderResponse;
import com.kbase.folder.entity.Folder;

import org.springframework.stereotype.Component;

/** Maps the persistence-only folder entity to the REST response. */
@Component
public class FolderMapper {

    public FolderResponse toResponse(Folder folder) {
        Objects.requireNonNull(folder, "folder");
        return new FolderResponse(
                folder.getId(),
                folder.getProject().getId(),
                folder.getParentId(),
                folder.getName(),
                folder.getCreatedAt(),
                folder.getUpdatedAt());
    }
}
