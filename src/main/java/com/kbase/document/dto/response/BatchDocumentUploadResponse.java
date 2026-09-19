package com.kbase.document.dto.response;

import java.util.List;

public record BatchDocumentUploadResponse(List<DocumentResponse> documents) {
}
