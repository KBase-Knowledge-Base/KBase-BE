package com.kbase.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kbase.config.properties.UploadProperties;
import com.kbase.shared.exception.KBaseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class FileValidationServiceTest {
    private FileValidationService service;
    private UploadProperties properties;

    @BeforeEach
    void setUp() {
        properties = new UploadProperties();
        properties.setDocumentMaxSize(DataSize.ofBytes(64));
        properties.setMaxBatchFiles(2);
        service = new FileValidationService(properties);
    }

    @Test
    void acceptsDetectedAndReportedPdfAndDerivesNormalizedFacts() {
        var file = new MockMultipartFile("file", "design.PDF", "application/pdf",
                "%PDF-1.4\nminimal".getBytes());
        ValidatedFile result = service.validate(file);
        assertThat(result.extension()).isEqualTo("pdf");
        assertThat(result.mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void rejectsEmptyUnsupportedMimeMismatchAndConfiguredOversize() {
        assertCode(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]), "FILE_EMPTY");
        assertCode(new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[] {1}),
                "UNSUPPORTED_FILE_TYPE");
        assertCode(new MockMultipartFile("file", "notes.txt", "application/pdf", "text".getBytes()),
                "MIME_TYPE_MISMATCH");
        assertCode(new MockMultipartFile("file", "large.txt", "text/plain", new byte[65]), "FILE_TOO_LARGE");
    }

    @Test
    void enforcesConfiguredBatchBounds() {
        assertThatThrownBy(() -> service.validateBatchCount(0)).isInstanceOf(KBaseException.class)
                .extracting(error -> ((KBaseException) error).getErrorCode().code())
                .isEqualTo("INVALID_FILE_METADATA");
        assertThatThrownBy(() -> service.validateBatchCount(3)).isInstanceOf(KBaseException.class);
    }

    private void assertCode(MockMultipartFile file, String code) {
        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(KBaseException.class)
                .extracting(error -> ((KBaseException) error).getErrorCode().code()).isEqualTo(code);
    }
}
