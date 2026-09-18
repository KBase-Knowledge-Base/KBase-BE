package com.kbase.document.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.kbase.document.dto.request.DocumentMetadataRequest;
import com.kbase.document.dto.request.UpdateDocumentRequest;
import com.kbase.document.dto.response.BatchDocumentUploadResponse;
import com.kbase.document.dto.response.DocumentResponse;
import com.kbase.document.service.DocumentService;
import com.kbase.document.service.FileDelivery;
import com.kbase.document.service.InvalidRangeException;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.response.ApiErrorResponse;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Authorized document lifecycle endpoints. No metadata search endpoint is introduced before M12. */
@RestController
@RequestMapping("/api/v1")
public class DocumentController {
    private final DocumentService documentService;
    private final CurrentUserService currentUserService;

    public DocumentController(DocumentService documentService, CurrentUserService currentUserService) {
        this.documentService = documentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(value = "/projects/{projectId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "metadata", required = false) DocumentMetadataRequest metadata) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.upload(
                projectId, file, metadata, currentUserService.requirePrincipal()));
    }

    @PostMapping(value = "/projects/{projectId}/documents/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchDocumentUploadResponse> batchUpload(@PathVariable UUID projectId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart(value = "metadata", required = false) DocumentMetadataRequest metadata) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.batchUpload(
                projectId, files, metadata, currentUserService.requirePrincipal()));
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID documentId) {
        return ResponseEntity.ok(documentService.getDocument(documentId, currentUserService.requirePrincipal()));
    }

    @PatchMapping("/documents/{documentId}")
    public ResponseEntity<DocumentResponse> updateDocument(@PathVariable UUID documentId,
            @RequestBody UpdateDocumentRequest request) {
        return ResponseEntity.ok(documentService.updateMetadata(
                documentId, request, currentUserService.requirePrincipal()));
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID documentId) {
        return stream(documentService.download(documentId, currentUserService.requirePrincipal()), false);
    }

    @GetMapping("/documents/{documentId}/preview")
    public ResponseEntity<?> preview(@PathVariable UUID documentId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        try {
            return stream(documentService.preview(documentId, currentUserService.requirePrincipal(), range), true);
        } catch (InvalidRangeException exception) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + exception.totalLength()).build();
        }
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentId) {
        documentService.delete(documentId, currentUserService.requirePrincipal());
        return ResponseEntity.noContent().build();
    }

    /**
     * A Resource response keeps the storage input stream on the response path;
     * it is deliberately not converted into a byte array. Spring MVC's resource
     * writer closes the input after the response body has been written.
     */
    private static ResponseEntity<InputStreamResource> stream(FileDelivery delivery, boolean inline) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(delivery.mimeType()));
        headers.setContentLength(delivery.contentLength());
        headers.setContentDisposition((inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(delivery.filename(), java.nio.charset.StandardCharsets.UTF_8).build());
        if (inline && "video/mp4".equals(delivery.mimeType())) headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        HttpStatus status = delivery.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
        if (delivery.partial()) headers.set(HttpHeaders.CONTENT_RANGE,
                "bytes %d-%d/%d".formatted(delivery.rangeStart(), delivery.rangeEnd(), delivery.totalLength()));
        return new ResponseEntity<>(new InputStreamResource(delivery.inputStream()), headers, status);
    }
}
