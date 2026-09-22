package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kbase.ai.entity.DocumentAiIndex;
import com.kbase.ai.enums.AiJobStatus;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.enums.DocumentAiIndexStatus;
import com.kbase.ai.repository.AiJobRepository;
import com.kbase.ai.repository.DocumentAiIndexRepository;
import com.kbase.ai.service.DocumentAiIntentService;
import com.kbase.ai.service.AiJobStore;
import com.kbase.document.dto.request.DocumentMetadataRequest;
import com.kbase.document.entity.Document;
import com.kbase.document.enums.FileKind;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.service.DocumentService;
import com.kbase.project.dto.request.CreateProjectRequest;
import com.kbase.project.entity.Project;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.project.service.ProjectService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.storage.service.StorageService;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** M3 document intent evidence on real PostgreSQL with StorageService still as the Core boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("local")
class DocumentAiIntentIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            com.kbase.integration.support.PostgresTestSupport.IMAGE)
            .withDatabaseName("kbase")
            .withUsername("kbase")
            .withPassword("kbase");

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("kbase.postgres.host", POSTGRES::getHost);
        registry.add("kbase.postgres.port", () -> POSTGRES.getFirstMappedPort());
        registry.add("kbase.postgres.database", () -> POSTGRES.getDatabaseName());
        registry.add("kbase.postgres.username", POSTGRES::getUsername);
        registry.add("kbase.postgres.password", POSTGRES::getPassword);
        registry.add("kbase.jwt.signing-secret", () -> "m3-document-intent-jwt-signing-secret-at-least-256-bits");
        registry.add("kbase.otp.hash-secret", () -> "m3-document-intent-otp-secret");
        registry.add("kbase.mail.username", () -> "m3-document@example.invalid");
        registry.add("kbase.mail.app-password", () -> "m3-document-mail-password");
        registry.add("kbase.storage.access-key", () -> "m3-document-access-key");
        registry.add("kbase.storage.secret-key", () -> "m3-document-storage-secret");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
    }

    @MockitoBean
    private StorageService storageService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentAiIntentService intentService;

    @Autowired
    private DocumentAiIndexRepository indexRepository;

    @Autowired
    private AiJobRepository jobRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AiJobStore jobStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearJobs() {
        jdbcTemplate.update("DELETE FROM ai_jobs");
    }

    @Test
    void supportedUploadCreatesPendingIndexAndExactlyOneDurableJob() {
        User owner = user("supported-owner");
        Project project = project(owner, "supported-upload");
        CustomUserPrincipal principal = principal(owner);

        var response = documentService.upload(project.getId(),
                new MockMultipartFile("file", "guide.pdf", "application/pdf",
                        "%PDF-1.4\nM3".getBytes()),
                new DocumentMetadataRequest(null, null, null, null, List.of()), principal);

        DocumentAiIndex index = indexRepository.findByDocumentIdAndProjectId(
                response.id(), project.getId()).orElseThrow();
        assertThat(index.getStatus()).isEqualTo(DocumentAiIndexStatus.PENDING);
        assertThat(index.getDesiredVersion()).isEqualTo(1L);
        assertThat(index.getChunkingVersion()).isEqualTo("chunk-v1");
        assertThat(index.getEmbeddingModel()).isEqualTo("gemini-embedding-2");
        assertThat(index.getEmbeddingDimensions()).isEqualTo(768);

        assertThat(jobRepository.findAllByDocumentId(response.id())).singleElement().satisfies(job -> {
            assertThat(job.getJobType()).isEqualTo(AiJobType.DOCUMENT_INDEX);
            assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING);
            assertThat(job.getDedupKey()).isEqualTo(
                    "document-index:" + response.id() + ":v1");
            assertThat(job.getPayload()).contains("schemaVersion", "desiredVersion");
        });
    }

    @Test
    void unsupportedCoreDocumentGetsUnsupportedStateWithoutAJob() {
        User owner = user("unsupported-owner");
        Project project = project(owner, "unsupported-upload");
        UUID documentId = UUID.randomUUID();
        Document document = new Document(project, owner, "sheet.xlsx", "sheet.xlsx",
                FileKind.DOCUMENT, "xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 12,
                "projects/" + project.getId() + "/documents/" + documentId + ".xlsx");
        document.setId(documentId);
        document = documentRepository.saveAndFlush(document);

        intentService.recordUploadIntent(document);

        assertThat(indexRepository.findByDocumentIdAndProjectId(documentId, project.getId()))
                .hasValueSatisfying(index -> {
                    assertThat(index.getStatus()).isEqualTo(DocumentAiIndexStatus.UNSUPPORTED);
                    assertThat(index.getFailureReason()).isEqualTo("UNSUPPORTED_FILE_TYPE");
                });
        assertThat(jobRepository.findAllByDocumentId(documentId)).isEmpty();
    }

    @Test
    void repeatedLifecycleCallbackDoesNotCreateAnotherActiveJob() {
        User owner = user("duplicate-owner");
        Project project = project(owner, "duplicate-upload");
        CustomUserPrincipal principal = principal(owner);
        var response = documentService.upload(project.getId(),
                new MockMultipartFile("file", "notes.txt", "text/plain", "M3".getBytes()),
                new DocumentMetadataRequest(null, null, null, null, List.of()), principal);
        Document document = documentRepository.findById(response.id()).orElseThrow();

        intentService.recordUploadIntent(document);

        assertThat(jobRepository.findAllByDocumentId(response.id())).hasSize(1);
        assertThat(indexRepository.findByDocumentIdAndProjectId(response.id(), project.getId()))
                .get().extracting(DocumentAiIndex::getStatus)
                .isEqualTo(DocumentAiIndexStatus.PENDING);
    }

    @Test
    void documentDeleteCascadesPendingAiStateAndJob() {
        User owner = user("delete-document-owner");
        Project project = project(owner, "delete-document");
        CustomUserPrincipal principal = principal(owner);
        var response = documentService.upload(project.getId(),
                new MockMultipartFile("file", "delete.pdf", "application/pdf",
                        "%PDF-1.4\nM3".getBytes()),
                new DocumentMetadataRequest(null, null, null, null, List.of()), principal);

        documentService.delete(response.id(), principal);

        assertThat(documentRepository.findById(response.id())).isEmpty();
        assertThat(indexRepository.findByDocumentIdAndProjectId(response.id(), project.getId()))
                .isEmpty();
        assertThat(jobRepository.findAllByDocumentId(response.id())).isEmpty();
    }

    @Test
    void documentDeleteRemovesClaimedJobAndRejectsLateWorkerTransition() {
        User owner = user("claimed-delete-owner");
        Project project = project(owner, "claimed-delete");
        CustomUserPrincipal principal = principal(owner);
        var response = documentService.upload(project.getId(),
                new MockMultipartFile("file", "claimed.pdf", "application/pdf",
                        "%PDF-1.4\nM3".getBytes()),
                new DocumentMetadataRequest(null, null, null, null, List.of()), principal);
        var job = jobRepository.findAllByDocumentId(response.id()).getFirst();
        var claim = jobStore.claimDueJobs(
                java.util.Set.of(AiJobType.DOCUMENT_INDEX), 1, job.getRunAt().plusSeconds(1))
                .getFirst();
        assertThat(claim.id()).isEqualTo(job.getId());

        documentService.delete(response.id(), principal);

        assertThat(jobRepository.findById(job.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from ai_jobs where id = ?", Long.class, job.getId()))
                .as("raw ai_jobs row after document delete").isZero();
        assertThat(jobStore.markDone(claim)).isFalse();
    }

    @Test
    void projectDeleteCascadesDocumentAiStateAndClaimedProjectJob() {
        User owner = user("delete-project-owner");
        Project project = project(owner, "delete-project");
        CustomUserPrincipal principal = principal(owner);
        var response = documentService.upload(project.getId(),
                new MockMultipartFile("file", "project.pdf", "application/pdf",
                        "%PDF-1.4\nM3".getBytes()),
                new DocumentMetadataRequest(null, null, null, null, List.of()), principal);
        var job = jobRepository.findAllByDocumentId(response.id()).getFirst();
        var claim = jobStore.claimDueJobs(
                java.util.Set.of(AiJobType.DOCUMENT_INDEX), 1, job.getRunAt().plusSeconds(1))
                .getFirst();
        assertThat(claim.id()).isEqualTo(job.getId());

        projectService.deleteProject(project.getId(), principal);

        assertThat(projectRepository.findById(project.getId())).isEmpty();
        assertThat(documentRepository.findById(response.id())).isEmpty();
        assertThat(indexRepository.findByDocumentIdAndProjectId(response.id(), project.getId()))
                .isEmpty();
        assertThat(jobRepository.findAllByProjectId(project.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from ai_jobs where id = ?", Long.class, job.getId()))
                .as("raw ai_jobs row after project delete").isZero();
        assertThat(jobStore.markDone(claim)).isFalse();
    }

    private User user(String localPart) {
        User user = new User(localPart + "-" + UUID.randomUUID() + "@example.com",
                "hash", localPart, SystemRole.USER, UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private Project project(User owner, String name) {
        UUID projectId = projectService.createProject(owner.getId(),
                new CreateProjectRequest(name + "-" + UUID.randomUUID(), null)).id();
        return projectRepository.findById(projectId).orElseThrow();
    }

    private CustomUserPrincipal principal(User user) {
        return new CustomUserPrincipal(user.getId(), user.getEmail(),
                SystemRole.USER, UserStatus.ACTIVE, true);
    }
}
