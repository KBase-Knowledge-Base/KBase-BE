package com.kbase.document.mapper;

import java.util.List;

import com.kbase.document.dto.response.DocumentCategoryResponse;
import com.kbase.document.dto.response.DocumentResponse;
import com.kbase.document.dto.response.DocumentSummaryResponse;
import com.kbase.document.dto.response.DocumentTagResponse;
import com.kbase.document.dto.response.DocumentUserResponse;
import com.kbase.document.entity.Document;
import com.kbase.document.entity.DocumentTag;

import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {
    /** Maps the deliberately shallow DTO used by the paginated browser. */
    public DocumentSummaryResponse toSummaryResponse(Document document) {
        return new DocumentSummaryResponse(document.getId(), document.getDisplayName(),
                document.getOriginalFilename(), document.getFileKind(), document.getExtension(),
                document.getMimeType(), document.getSizeBytes(), document.getFolderId(),
                document.getCreatedAt(), document.getUpdatedAt());
    }

    public DocumentResponse toResponse(Document document, List<DocumentTag> relations) {
        var category = document.getCategory();
        return new DocumentResponse(document.getId(), document.getProject().getId(),
                new DocumentUserResponse(document.getUploadedBy().getId(), document.getUploadedBy().getDisplayName()),
                document.getFolderId(), category == null ? null
                        : new DocumentCategoryResponse(category.getId(), category.getName()),
                relations.stream().map(DocumentTag::getTag)
                        .map(tag -> new DocumentTagResponse(tag.getId(), tag.getName())).toList(),
                document.getDisplayName(), document.getOriginalFilename(), document.getFileKind(),
                document.getExtension(), document.getMimeType(), document.getSizeBytes(),
                document.getDescription(), document.getCreatedAt(), document.getUpdatedAt());
    }
}
