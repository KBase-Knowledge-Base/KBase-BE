package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kbase.document.dto.request.DocumentMetadataRequest;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.repository.DocumentTagRepository;
import com.kbase.document.service.DocumentService;
import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.dto.request.CreateProjectRequest;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.project.service.ProjectService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.storage.exception.StorageObjectNotFoundException;
import com.kbase.storage.service.StorageService;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Critical M11 PostgreSQL + MinIO journey: stream upload, then document hard delete. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "kbase.jwt.signing-secret=m11-integration-jwt-signing-secret-256-bits!",
        "kbase.otp.hash-secret=m11-integration-otp-hash-secret",
        "kbase.mail.username=m11@example.invalid", "kbase.mail.app-password=m11-mail-password",
        "kbase.storage.access-key=minioadmin", "kbase.storage.secret-key=minioadmin",
        "kbase.storage.bucket=m11-documents", "kbase.storage.auto-create-bucket=false",
        "kbase.storage.initialize-on-startup=false"
})
class DocumentLifecycleStorageIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(com.kbase.integration.support.PostgresTestSupport.IMAGE);
    @Container static final GenericContainer<?> MINIO = new GenericContainer<>("quay.io/minio/minio:latest")
            .withExposedPorts(9000).withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin").withCommand("server", "/data");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("kbase.storage.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    }

    @BeforeAll static void createBucket() throws Exception {
        MinioClient.builder().endpoint("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000))
                .credentials("minioadmin", "minioadmin").build()
                .makeBucket(MakeBucketArgs.builder().bucket("m11-documents").build());
    }

    @Autowired private DocumentService documentService;
    @Autowired private ProjectService projectService;
    @Autowired private UserRepository users;
    @Autowired private ProjectRepository projects;
    @Autowired private com.kbase.project.repository.ProjectMemberRepository members;
    @Autowired private DocumentRepository documents;
    @Autowired private DocumentTagRepository documentTags;
    @Autowired private StorageService storage;

    @Test
    void uploadStreamsToMinioAndDocumentHardDeleteRemovesBinaryAndRelationalMetadata() throws Exception {
        User owner = new User("m11-" + UUID.randomUUID() + "@example.com", "hash", "Owner",
                SystemRole.USER, UserStatus.ACTIVE);
        owner.setEmailVerifiedAt(Instant.now()); owner = users.saveAndFlush(owner);
        UUID projectId = projectService.createProject(owner.getId(),
                new CreateProjectRequest("M11 " + UUID.randomUUID(), null)).id();
        Project project = projects.findById(projectId).orElseThrow();
        CustomUserPrincipal principal = new CustomUserPrincipal(owner.getId(), owner.getEmail(),
                SystemRole.USER, UserStatus.ACTIVE, true);
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf",
                "%PDF-1.4\nM11 proof".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        var uploaded = documentService.upload(project.getId(), file,
                new DocumentMetadataRequest(null, "proof", null, null, List.of()), principal);
        String key = documents.findStorageKeysByProjectId(project.getId()).getFirst().getStorageKey();
        assertThat(uploaded.displayName()).isEqualTo("proof.pdf");
        var resource = storage.get(key);
        try (var input = resource.inputStream(); var copy = new ByteArrayOutputStream()) {
            input.transferTo(copy);
            assertThat(copy.toByteArray()).containsExactly(file.getBytes());
        }

        documentService.delete(uploaded.id(), principal);
        assertThat(projects.findById(project.getId())).isPresent();
        assertThat(documents.findById(uploaded.id())).isEmpty();
        assertThat(documentTags.findAllByIdDocumentId(uploaded.id())).isEmpty();
        assertThatThrownBy(() -> storage.stat(key)).isInstanceOf(StorageObjectNotFoundException.class);
    }
}
