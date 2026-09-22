package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.kbase.ai.repository.AiJobRepository;
import com.kbase.ai.repository.DocumentAiIndexRepository;
import com.kbase.document.dto.request.DocumentMetadataRequest;
import com.kbase.document.dto.response.DocumentResponse;
import com.kbase.document.entity.Document;
import com.kbase.document.mapper.DocumentMapper;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Proves Core + AI persistence and StorageService compensation share one upload transaction. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("local")
class DocumentAiRollbackIntegrationTest {

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
        registry.add("kbase.jwt.signing-secret", () -> "m3-document-rollback-jwt-signing-secret-at-least-256-bits");
        registry.add("kbase.otp.hash-secret", () -> "m3-document-rollback-otp-secret");
        registry.add("kbase.mail.username", () -> "m3-document-rollback@example.invalid");
        registry.add("kbase.mail.app-password", () -> "m3-document-rollback-mail-password");
        registry.add("kbase.storage.access-key", () -> "m3-document-rollback-access-key");
        registry.add("kbase.storage.secret-key", () -> "m3-document-rollback-storage-secret");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
    }

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentAiIndexRepository indexRepository;

    @Autowired
    private AiJobRepository jobRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void resetMocks() {
        reset(storageService, documentMapper);
    }

    @Test
    void singleUploadFailureAfterAiJobInsertRollsBackDocumentAiStateAndCompensatesStorage() {
        User owner = user("single-rollback-owner");
        Project project = project(owner, "single-rollback");
        when(documentMapper.toResponse(any(Document.class), anyList()))
                .thenThrow(new IllegalStateException("response mapping failure"));

        assertThatThrownBy(() -> documentService.upload(project.getId(),
                pdf("single.pdf"), emptyMetadata(), principal(owner)))
                .isInstanceOf(RuntimeException.class);

        assertThat(documentCount(project)).isZero();
        assertThat(indexRepository.findAll()).isEmpty();
        assertThat(jobRepository.findAllByProjectId(project.getId())).isEmpty();
        verify(storageService).delete(any(String.class));
    }

    @Test
    void batchFailureRollsBackEarlierDocumentAiStateAndCompensatesEveryObject() {
        User owner = user("batch-rollback-owner");
        Project project = project(owner, "batch-rollback");
        AtomicInteger mapperCalls = new AtomicInteger();
        when(documentMapper.toResponse(any(Document.class), anyList())).thenAnswer(invocation -> {
            if (mapperCalls.incrementAndGet() == 2) {
                throw new IllegalStateException("second response mapping failure");
            }
            return null;
        });

        assertThatThrownBy(() -> documentService.batchUpload(project.getId(),
                List.of(pdf("first.pdf"), pdf("second.pdf")), emptyMetadata(), principal(owner)))
                .isInstanceOf(RuntimeException.class);

        assertThat(documentCount(project)).isZero();
        assertThat(indexRepository.findAll()).isEmpty();
        assertThat(jobRepository.findAllByProjectId(project.getId())).isEmpty();
        verify(storageService, org.mockito.Mockito.times(2)).delete(any(String.class));
    }

    private long documentCount(Project project) {
        return documentRepository.countByProjectId(project.getId());
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

    private static MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("files", filename, "application/pdf", "%PDF-1.4\nM3".getBytes());
    }

    private static DocumentMetadataRequest emptyMetadata() {
        return new DocumentMetadataRequest(null, null, null, null, List.of());
    }

    private static CustomUserPrincipal principal(User user) {
        return new CustomUserPrincipal(user.getId(), user.getEmail(),
                SystemRole.USER, UserStatus.ACTIVE, true);
    }
}
