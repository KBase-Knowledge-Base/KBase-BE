package com.kbase.document.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kbase.config.OpenApiConfig;
import com.kbase.document.dto.request.DocumentMetadataRequest;
import com.kbase.document.dto.request.UpdateDocumentRequest;
import com.kbase.document.dto.response.BatchDocumentUploadResponse;
import com.kbase.document.dto.response.DocumentResponse;
import com.kbase.document.dto.response.DocumentSummaryResponse;
import com.kbase.document.enums.FileKind;
import com.kbase.document.service.DocumentSearchCriteria;
import com.kbase.document.service.DocumentSearchService;
import com.kbase.document.service.DocumentService;
import com.kbase.document.service.FileDelivery;
import com.kbase.document.service.InvalidRangeException;
import com.kbase.security.service.CurrentUserService;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.shared.pagination.PaginationParser;
import com.kbase.shared.response.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Authorized document lifecycle and metadata-only search endpoints. */
@Tag(name = OpenApiConfig.TAG_DOCUMENTS,
        description = "Documents live inside projects. Reading requires project membership (MEMBER/OWNER/ADMIN); "
                + "modifying or deleting a document requires being its uploader (MEMBER) or project OWNER/system ADMIN. "
                + "Binaries stream through the backend from private storage; storage keys are never exposed.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)
@RestController
@RequestMapping("/api/v1")
public class DocumentController {
    private static final List<String> SORTABLE_FIELDS =
            List.of("displayName", "createdAt", "updatedAt", "sizeBytes");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final DocumentService documentService;
    private final DocumentSearchService documentSearchService;
    private final CurrentUserService currentUserService;
    private final PaginationParser paginationParser;

    public DocumentController(DocumentService documentService,
            DocumentSearchService documentSearchService,
            CurrentUserService currentUserService,
            PaginationParser paginationParser) {
        this.documentService = documentService;
        this.documentSearchService = documentSearchService;
        this.currentUserService = currentUserService;
        this.paginationParser = paginationParser;
    }

    @Operation(summary = "Search document metadata",
            description = "Project-scoped, metadata-only search. Access: project MEMBER, OWNER, or system ADMIN. "
                    + "q matches displayName, originalFilename, description and category/tag names; Core v1 searches no "
                    + "file content, transcript or embedding. Sortable fields: displayName, createdAt, updatedAt, "
                    + "sizeBytes (default createdAt,desc).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged document metadata summaries"),
            @ApiResponse(responseCode = "400", description = "INVALID_PARAMETER — an unknown sort field or malformed filter",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller is not a member of the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/projects/{projectId}/documents")
    public ResponseEntity<PageResponse<DocumentSummaryResponse>> search(
            @PathVariable UUID projectId,
            @Parameter(description = "Case-insensitive metadata text search across display name, original filename, "
                    + "description and category/tag names")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Filter by containing folder")
            @RequestParam(name = "folderId", required = false) UUID folderId,
            @Parameter(description = "Filter by category")
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @Parameter(description = "Filter by tag")
            @RequestParam(name = "tagId", required = false) UUID tagId,
            @Parameter(description = "Filter by file kind")
            @RequestParam(name = "fileKind", required = false) FileKind fileKind,
            @Parameter(description = "Filter by uploader user id")
            @RequestParam(name = "uploadedBy", required = false) UUID uploadedBy,
            @Parameter(description = "ISO-8601 instant lower bound on creation time, e.g. 2026-09-17T00:00:00Z")
            @RequestParam(name = "createdFrom", required = false) Instant createdFrom,
            @Parameter(description = "ISO-8601 instant upper bound on creation time")
            @RequestParam(name = "createdTo", required = false) Instant createdTo,
            @Parameter(description = "Zero-based page index (default 0)")
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size, 1..100 (default 20)")
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "Sort as field,direction; allowed fields: displayName, createdAt, updatedAt, sizeBytes")
            @RequestParam(name = "sort", required = false) String sort) {
        Pageable pageable = paginationParser.parse(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        DocumentSearchCriteria criteria = new DocumentSearchCriteria(q, folderId, categoryId, tagId,
                fileKind, uploadedBy, createdFrom, createdTo);
        return ResponseEntity.ok(documentSearchService.search(
                projectId, criteria, currentUserService.requirePrincipal(), pageable));
    }

    @Operation(summary = "Upload document",
            description = "Uploads one supported file (documents/office up to 50 MB, images up to 20 MB, videos up to "
                    + "500 MB by default; limits are configuration-driven). Requires project membership — any member "
                    + "may upload. The optional metadata JSON part must reference folders, categories and tags of the "
                    + "same project. displayName defaults to the original filename.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document uploaded and persisted"),
            @ApiResponse(responseCode = "400", description = "FILE_EMPTY — the file has no content, or "
                    + "INVALID_FILE_METADATA — metadata references invalid or cross-project folder/category/tags, or "
                    + "VALIDATION_ERROR — the metadata JSON is malformed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller is not a member of the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "FOLDER_NOT_FOUND, CATEGORY_NOT_FOUND or TAG_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE — the file exceeds the kind-specific size limit",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "UNSUPPORTED_FILE_TYPE — the extension is not supported, or "
                    + "MIME_TYPE_MISMATCH — the detected media type contradicts the filename",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "FILE_UPLOAD_FAILED — the upload could not be completed; "
                    + "any partial state is compensated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "STORAGE_SERVICE_UNAVAILABLE — MinIO is unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(value = "/projects/{projectId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@PathVariable UUID projectId,
            @Parameter(description = "Binary file part")
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Optional metadata JSON part: displayName, description, folderId, categoryId, tagIds")
            @RequestPart(value = "metadata", required = false) DocumentMetadataRequest metadata) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.upload(
                projectId, file, metadata, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Upload documents in batch",
            description = "Uploads up to 10 files (configurable) with one shared optional metadata part applied to all "
                    + "of them; per-file metadata is not supported. Application-level all-or-fail: on failure uploaded "
                    + "binaries are cleaned up and nothing persists.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "All documents uploaded and persisted"),
            @ApiResponse(responseCode = "400", description = "FILE_EMPTY, INVALID_FILE_METADATA, VALIDATION_ERROR or "
                    + "batch size above the configured maximum",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller is not a member of the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "FOLDER_NOT_FOUND, CATEGORY_NOT_FOUND or TAG_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE — a file exceeds its kind-specific size limit",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "UNSUPPORTED_FILE_TYPE or MIME_TYPE_MISMATCH",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "FILE_UPLOAD_FAILED — a file could not be uploaded; "
                    + "uploaded binaries are cleaned up",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "STORAGE_SERVICE_UNAVAILABLE — MinIO is unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(value = "/projects/{projectId}/documents/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchDocumentUploadResponse> batchUpload(@PathVariable UUID projectId,
            @Parameter(description = "Binary file parts; maximum 10 files (configurable)")
            @RequestPart("files") List<MultipartFile> files,
            @Parameter(description = "Optional metadata JSON part shared by all files")
            @RequestPart(value = "metadata", required = false) DocumentMetadataRequest metadata) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.batchUpload(
                projectId, files, metadata, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Get document metadata",
            description = "Access: project MEMBER, OWNER, or system ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document metadata"),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller has no access to the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "DOCUMENT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/documents/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID documentId) {
        return ResponseEntity.ok(documentService.getDocument(documentId, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Update document metadata",
            description = "Renaming changes displayName only, never the storage key. Permissions: MEMBER may update "
                    + "only documents they uploaded; OWNER may update any document in their project; ADMIN has the "
                    + "system-level override. Updating tags replaces the full tag list; a null tagIds keeps existing tags.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metadata updated"),
            @ApiResponse(responseCode = "400", description = "INVALID_FILE_METADATA — folder/category/tag rules violated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "DOCUMENT_MODIFICATION_FORBIDDEN — MEMBER is not the uploader",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "DOCUMENT_NOT_FOUND, FOLDER_NOT_FOUND, CATEGORY_NOT_FOUND or TAG_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/documents/{documentId}")
    public ResponseEntity<DocumentResponse> updateDocument(@PathVariable UUID documentId,
            @RequestBody UpdateDocumentRequest request) {
        return ResponseEntity.ok(documentService.updateMetadata(
                documentId, request, currentUserService.requirePrincipal()));
    }

    @Operation(summary = "Download document",
            description = "Streams the binary as an attachment (Content-Disposition: attachment, filename from "
                    + "displayName). Access: project MEMBER, OWNER, or system ADMIN. The actual Content-Type is the "
                    + "stored MIME type of the file; the storage bucket stays private and no storage key is exposed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Binary attachment stream",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")),
                    headers = @Header(name = "Content-Disposition", description = "attachment; filename from displayName")),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller has no access to the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "DOCUMENT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "STORAGE_SERVICE_UNAVAILABLE — MinIO is unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID documentId) {
        return stream(documentService.download(documentId, currentUserService.requirePrincipal()), false);
    }

    @Operation(summary = "Preview document",
            description = "Streams the binary inline (Content-Disposition: inline, filename from displayName) for "
                    + "previewable kinds: PDF, images, TXT, MD and MP4. Office files return 415 PREVIEW_NOT_SUPPORTED. "
                    + "MP4 previews accept a single HTTP Range header; a satisfiable range returns 206 Partial Content "
                    + "with Content-Range, and an unsatisfiable range returns 416 with Content-Range: bytes */total. "
                    + "Access: project MEMBER, OWNER, or system ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Full inline preview stream",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")),
                    headers = @Header(name = "Content-Disposition", description = "inline; filename from displayName")),
            @ApiResponse(responseCode = "206", description = "Partial content for a satisfiable single byte range (MP4)",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")),
                    headers = @Header(name = "Content-Range", description = "bytes <start>-<end>/<total>")),
            @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_FORBIDDEN — the caller has no access to the project",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "DOCUMENT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "PREVIEW_NOT_SUPPORTED — the file kind has no browser preview",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "416", description = "Requested range not satisfiable; no body",
                    headers = @Header(name = "Content-Range", description = "bytes */<total>")),
            @ApiResponse(responseCode = "503", description = "STORAGE_SERVICE_UNAVAILABLE — MinIO is unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/documents/{documentId}/preview")
    public ResponseEntity<?> preview(@PathVariable UUID documentId,
            @Parameter(in = ParameterIn.HEADER, name = "Range", required = false,
                    description = "Optional single byte range for MP4 preview, e.g. bytes=0-1048575")
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        try {
            return stream(documentService.preview(documentId, currentUserService.requirePrincipal(), range), true);
        } catch (InvalidRangeException exception) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + exception.totalLength()).build();
        }
    }

    @Operation(summary = "Delete document",
            description = "Hard delete, storage-first: the MinIO object is deleted before the database row and tag "
                    + "assignments cascade. Permissions: MEMBER may delete only documents they uploaded; OWNER may "
                    + "delete any document in their project; ADMIN has the system-level override.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document and binary deleted; no response body"),
            @ApiResponse(responseCode = "403", description = "DOCUMENT_MODIFICATION_FORBIDDEN — MEMBER is not the uploader",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "DOCUMENT_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "DOCUMENT_DELETE_FAILED — the deletion could not complete",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "STORAGE_SERVICE_UNAVAILABLE — MinIO is unavailable, so nothing was deleted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
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
