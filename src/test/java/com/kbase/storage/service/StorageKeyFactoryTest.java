package com.kbase.storage.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StorageKeyFactoryTest {

    private final StorageKeyFactory factory = new StorageKeyFactory();

    @Test
    void createsBackendControlledProjectDocumentKeyWithNormalizedExtension() {
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        assertThat(factory.documentObjectKey(projectId, documentId, " PDF "))
                .isEqualTo("projects/%s/documents/%s.pdf".formatted(projectId, documentId));
    }

    @Test
    void rejectsUnsafeOrMissingExtensions() {
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.documentObjectKey(projectId, documentId, "pdf/../../secret"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.documentObjectKey(projectId, documentId, " "));
    }
}
