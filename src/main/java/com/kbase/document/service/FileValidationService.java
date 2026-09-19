package com.kbase.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.kbase.config.properties.UploadProperties;
import com.kbase.document.enums.FileKind;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Validates upload content without trusting a client filename or content type. */
@Service
public class FileValidationService {

    private static final Map<String, FileKind> KINDS = Map.ofEntries(
            Map.entry("pdf", FileKind.DOCUMENT), Map.entry("doc", FileKind.DOCUMENT),
            Map.entry("docx", FileKind.DOCUMENT), Map.entry("xls", FileKind.DOCUMENT),
            Map.entry("xlsx", FileKind.DOCUMENT), Map.entry("ppt", FileKind.DOCUMENT),
            Map.entry("pptx", FileKind.DOCUMENT), Map.entry("md", FileKind.DOCUMENT),
            Map.entry("txt", FileKind.DOCUMENT), Map.entry("jpg", FileKind.IMAGE),
            Map.entry("jpeg", FileKind.IMAGE), Map.entry("png", FileKind.IMAGE),
            Map.entry("gif", FileKind.IMAGE), Map.entry("svg", FileKind.IMAGE),
            Map.entry("bmp", FileKind.IMAGE), Map.entry("mp4", FileKind.VIDEO),
            Map.entry("mov", FileKind.VIDEO), Map.entry("avi", FileKind.VIDEO));
    private static final Map<String, Set<String>> MIME_TYPES = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("doc", Set.of("application/msword")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xls", Set.of("application/vnd.ms-excel")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("md", Set.of("text/markdown", "text/plain")), Map.entry("txt", Set.of("text/plain")),
            Map.entry("jpg", Set.of("image/jpeg")), Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")), Map.entry("gif", Set.of("image/gif")),
            Map.entry("svg", Set.of("image/svg+xml")), Map.entry("bmp", Set.of("image/bmp", "image/x-ms-bmp")),
            Map.entry("mp4", Set.of("video/mp4")), Map.entry("mov", Set.of("video/quicktime")),
            Map.entry("avi", Set.of("video/x-msvideo")));

    private final UploadProperties properties;
    private final Tika tika = new Tika();

    public FileValidationService(UploadProperties properties) {
        this.properties = properties;
    }

    public void validateBatchCount(int count) {
        if (count < 1 || count > properties.getMaxBatchFiles()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_METADATA,
                    "The number of uploaded files is invalid.");
        }
    }

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank() || original.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_FILE_METADATA);
        }
        String extension = extensionOf(original);
        FileKind kind = KINDS.get(extension);
        if (kind == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (file.getSize() > limitFor(kind)) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String detected = detect(file, original);
        if (!MIME_TYPES.get(extension).contains(detected)) {
            throw new BusinessException(ErrorCode.MIME_TYPE_MISMATCH);
        }
        String reported = normalizeMime(file.getContentType());
        if (reported != null && !MIME_TYPES.get(extension).contains(reported)) {
            throw new BusinessException(ErrorCode.MIME_TYPE_MISMATCH);
        }
        return new ValidatedFile(original, extension, detected, kind, file.getSize());
    }

    private long limitFor(FileKind kind) {
        return switch (kind) {
            case DOCUMENT -> properties.getDocumentMaxSize().toBytes();
            case IMAGE -> properties.getImageMaxSize().toBytes();
            case VIDEO -> properties.getVideoMaxSize().toBytes();
        };
    }

    private String detect(MultipartFile file, String name) {
        try (InputStream stream = file.getInputStream()) {
            return normalizeMime(tika.detect(stream, name));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_FILE_METADATA);
        }
    }

    private static String extensionOf(String original) {
        String filename = original.replace('\\', '/');
        int slash = filename.lastIndexOf('/');
        int dot = filename.lastIndexOf('.');
        if (dot <= slash + 1 || dot == filename.length() - 1) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return null;
        }
        int semicolon = mime.indexOf(';');
        return (semicolon < 0 ? mime : mime.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    }
}
