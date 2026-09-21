package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.kbase.category.entity.Category;
import com.kbase.category.repository.CategoryRepository;
import com.kbase.document.entity.Document;
import com.kbase.document.entity.DocumentTag;
import com.kbase.document.entity.DocumentTagId;
import com.kbase.document.enums.FileKind;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.repository.DocumentTagRepository;
import com.kbase.folder.entity.Folder;
import com.kbase.folder.repository.FolderRepository;
import com.kbase.folder.service.FolderService;
import com.kbase.folder.dto.request.UpdateFolderRequest;
import com.kbase.mail.service.MailService;
import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.tag.entity.Tag;
import com.kbase.tag.repository.TagRepository;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import jakarta.servlet.http.Cookie;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * M9 API/security/transaction matrix against PostgreSQL and the real filter
 * chain. Document rows are seeded only to exercise the already-designed
 * folder/category/tag delete dependencies; no document API is implemented.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "spring.main.web-application-type=servlet",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "kbase.postgres.username=it-user",
        "kbase.postgres.password=it-password",
        "kbase.jwt.signing-secret=integration-test-jwt-signing-secret-256-bits!",
        "kbase.otp.hash-secret=integration-test-otp-hash-secret",
        "kbase.mail.username=it@example.invalid",
        "kbase.mail.app-password=it-mail-app-password",
        "kbase.storage.access-key=it-access-key",
        "kbase.storage.secret-key=it-storage-secret-key"
})
class OrganizationIntegrationTest {

    private static final String PASSWORD = "ExamplePassword123";
    private static final String REFRESH_COOKIE = "kbase_refresh_token";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("kbase.redis.host", REDIS::getHost);
        registry.add("kbase.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MailService mailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private FolderService folderService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentTagRepository documentTagRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private record VerifiedUser(User user, String token, Cookie refreshCookie) {
    }

    private record AdminAccount(User user, String token) {
    }

    private static String uniqueEmail() {
        return "org-" + UUID.randomUUID() + "@example.com";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode json(MvcResult result) throws java.io.IOException {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private VerifiedUser newVerifiedUser() throws Exception {
        String email = uniqueEmail();
        Mockito.clearInvocations(mailService);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Org User"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(mailService).sendEmailVerificationOtp(
                Mockito.eq(email), otpCaptor.capture(), Mockito.any());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, otpCaptor.getValue())))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new VerifiedUser(
                userRepository.findByEmail(email).orElseThrow(),
                json(login).get("accessToken").asString(),
                login.getResponse().getCookie(REFRESH_COOKIE));
    }

    private AdminAccount newAdmin() throws Exception {
        String email = "org-admin-" + UUID.randomUUID() + "@example.com";
        User admin = new User(email, passwordEncoder.encode("AdminPassword123"),
                "Organization Admin", SystemRole.ADMIN, UserStatus.ACTIVE);
        admin.setEmailVerifiedAt(Instant.now());
        admin = userRepository.save(admin);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"AdminPassword123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new AdminAccount(admin, json(login).get("accessToken").asString());
    }

    private UUID createProject(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Organization Project %s","description":"M9"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(result).get("id").asString());
    }

    private void addMember(UUID projectId, User user) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        projectMemberRepository.save(new ProjectMember(project, user, ProjectRole.MEMBER));
    }

    private Folder folder(UUID projectId, UUID folderId) {
        return folderRepository.findByIdAndProjectId(folderId, projectId).orElseThrow();
    }

    @Test
    void membersOwnersAndAdminsCanListOrganizationResources() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        VerifiedUser member = newVerifiedUser();
        AdminAccount admin = newAdmin();
        UUID projectId = createProject(owner.token());
        addMember(projectId, member.user());

        mockMvc.perform(post("/api/v1/projects/{projectId}/folders", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Backend\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/projects/{projectId}/folders", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Managed\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/projects/{projectId}/categories", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Technical\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/projects/{projectId}/tags", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"backend\"}"))
                .andExpect(status().isCreated());

        for (String resource : new String[] {"folders", "categories", "tags"}) {
            mockMvc.perform(get("/api/v1/projects/{projectId}/{resource}", projectId, resource)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/projects/{projectId}/{resource}", projectId, resource)
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/projects/{projectId}/{resource}", projectId, resource)
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void memberCannotManageFoldersOrCategories() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        VerifiedUser member = newVerifiedUser();
        UUID projectId = createProject(owner.token());
        addMember(projectId, member.user());

        MvcResult folder = mockMvc.perform(post("/api/v1/projects/{projectId}/folders", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Docs\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID folderId = UUID.fromString(json(folder).get("id").asString());

        MvcResult category = mockMvc.perform(post("/api/v1/projects/{projectId}/categories", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Technical\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID categoryId = UUID.fromString(json(category).get("id").asString());

        mockMvc.perform(post("/api/v1/projects/{projectId}/folders", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Child\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_MANAGEMENT_FORBIDDEN"));
        mockMvc.perform(patch("/api/v1/projects/{projectId}/folders/{folderId}", projectId, folderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/projects/{projectId}/folders/{folderId}", projectId, folderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/projects/{projectId}/categories", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Another\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/projects/{projectId}/categories/{categoryId}", projectId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/projects/{projectId}/categories/{categoryId}", projectId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void folderUniquenessSameProjectParentAndCycleRulesAreEnforced() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        VerifiedUser otherOwner = newVerifiedUser();
        UUID projectId = createProject(owner.token());
        UUID otherProjectId = createProject(otherOwner.token());

        MvcResult root = createFolder(owner.token(), projectId, "Specs", null);
        UUID rootId = UUID.fromString(json(root).get("id").asString());
        MvcResult otherParent = createFolder(owner.token(), projectId, "Other", null);
        UUID otherParentId = UUID.fromString(json(otherParent).get("id").asString());
        MvcResult child = createFolder(owner.token(), projectId, "Nested", rootId);
        UUID childId = UUID.fromString(json(child).get("id").asString());

        mockMvc.perform(post("/api/v1/projects/{projectId}/folders", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"specs\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NAME_ALREADY_EXISTS"));
        createFolder(owner.token(), projectId, "Specs", otherParentId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/folders", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NESTED\",\"parentId\":\"%s\"}".formatted(rootId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NAME_ALREADY_EXISTS"));

        UUID crossProjectParent = UUID.fromString(json(
                createFolder(otherOwner.token(), otherProjectId, "Foreign", null)).get("id").asString());
        mockMvc.perform(post("/api/v1/projects/{projectId}/folders", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cross\",\"parentId\":\"%s\"}".formatted(crossProjectParent)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARENT_FOLDER_NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/projects/{projectId}/folders/{folderId}", projectId, rootId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"%s\"}".formatted(rootId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_CYCLE_DETECTED"));
        mockMvc.perform(patch("/api/v1/projects/{projectId}/folders/{folderId}", projectId, rootId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"%s\"}".formatted(childId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_CYCLE_DETECTED"));
    }

    @Test
    void concurrentOppositeFolderMovesCreateNoCycle() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        UUID projectId = createProject(owner.token());
        UUID firstId = UUID.fromString(json(
                createFolder(owner.token(), projectId, "First", null)).get("id").asString());
        UUID secondId = UUID.fromString(json(
                createFolder(owner.token(), projectId, "Second", null)).get("id").asString());
        CustomUserPrincipal ownerPrincipal = new CustomUserPrincipal(
                owner.user().getId(), owner.user().getEmail(),
                SystemRole.USER, UserStatus.ACTIVE, true);

        // Two opposite moves race on the deterministic two-row lock; exactly
        // one may win and the loser must re-read the committed graph.
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Optional<ErrorCode>>> outcomes = new ArrayList<>();
            outcomes.add(executor.submit(() -> {
                start.await();
                try {
                    folderService.updateFolder(projectId, firstId,
                            moveRequest(secondId), ownerPrincipal);
                    return Optional.<ErrorCode>empty();
                } catch (com.kbase.shared.exception.BusinessException exception) {
                    return Optional.of(exception.getErrorCode());
                }
            }));
            outcomes.add(executor.submit(() -> {
                start.await();
                try {
                    folderService.updateFolder(projectId, secondId,
                            moveRequest(firstId), ownerPrincipal);
                    return Optional.<ErrorCode>empty();
                } catch (com.kbase.shared.exception.BusinessException exception) {
                    return Optional.of(exception.getErrorCode());
                }
            }));
            start.countDown();

            int successes = 0;
            var errors = new ArrayList<ErrorCode>();
            for (Future<Optional<ErrorCode>> outcome : outcomes) {
                var result = outcome.get(30, TimeUnit.SECONDS);
                if (result.isEmpty()) {
                    successes++;
                } else {
                    errors.add(result.get());
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(errors).containsExactly(ErrorCode.FOLDER_CYCLE_DETECTED);

            // The persisted parent graph must remain acyclic either way.
            Folder first = folder(projectId, firstId);
            Folder second = folder(projectId, secondId);
            boolean twoNodeCycle = first.getParentId() != null
                    && first.getParentId().equals(secondId)
                    && second.getParentId() != null
                    && second.getParentId().equals(firstId);
            assertThat(twoNodeCycle).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    private UpdateFolderRequest moveRequest(UUID parentId) {
        UpdateFolderRequest request = new UpdateFolderRequest();
        request.setParentId(parentId);
        return request;
    }

    @Test
    void folderDeleteRequiresEmptyFolder() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        UUID projectId = createProject(owner.token());
        MvcResult parentResult = createFolder(owner.token(), projectId, "Parent", null);
        UUID parentId = UUID.fromString(json(parentResult).get("id").asString());
        MvcResult childResult = createFolder(owner.token(), projectId, "Child", parentId);
        UUID childId = UUID.fromString(json(childResult).get("id").asString());

        mockMvc.perform(delete("/api/v1/projects/{projectId}/folders/{folderId}", projectId, parentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NOT_EMPTY"));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/folders/{folderId}", projectId, childId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNoContent());

        Project project = projectRepository.findById(projectId).orElseThrow();
        Folder documentFolder = folderRepository.findByIdAndProjectId(parentId, projectId).orElseThrow();
        Document folderDocument = new Document(
                project, owner.user(), "document.pdf", "document.pdf", FileKind.DOCUMENT,
                "pdf", "application/pdf", 100,
                "projects/%s/documents/%s.pdf".formatted(projectId, UUID.randomUUID()));
        folderDocument.setFolder(documentFolder);
        documentRepository.saveAndFlush(folderDocument);
        mockMvc.perform(delete("/api/v1/projects/{projectId}/folders/{folderId}", projectId, parentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NOT_EMPTY"));

        documentRepository.deleteAll();
        mockMvc.perform(delete("/api/v1/projects/{projectId}/folders/{folderId}", projectId, parentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNoContent());
    }

    @Test
    void categoryUniquenessInUseAndAdminManagementAreEnforced() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        AdminAccount admin = newAdmin();
        UUID projectId = createProject(owner.token());
        MvcResult category = mockMvc.perform(post("/api/v1/projects/{projectId}/categories", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Technical\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID categoryId = UUID.fromString(json(category).get("id").asString());

        mockMvc.perform(post("/api/v1/projects/{projectId}/categories", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"technical\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"));
        mockMvc.perform(patch("/api/v1/projects/{projectId}/categories/{categoryId}", projectId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Architecture\"}"))
                .andExpect(status().isOk());

        Project project = projectRepository.findById(projectId).orElseThrow();
        Category renamed = categoryRepository.findByIdAndProjectId(categoryId, projectId).orElseThrow();
        Document categoryDocument = new Document(
                project, owner.user(), "architecture.pdf", "architecture.pdf", FileKind.DOCUMENT,
                "pdf", "application/pdf", 100,
                "projects/%s/documents/%s.pdf".formatted(projectId, UUID.randomUUID()));
        categoryDocument.setCategory(renamed);
        documentRepository.saveAndFlush(categoryDocument);
        mockMvc.perform(delete("/api/v1/projects/{projectId}/categories/{categoryId}", projectId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        documentRepository.deleteAll();
        mockMvc.perform(delete("/api/v1/projects/{projectId}/categories/{categoryId}", projectId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isNoContent());
    }

    @Test
    void memberCreatesTagButCannotMutateAndDeletePreservesDocument() throws Exception {
        VerifiedUser owner = newVerifiedUser();
        VerifiedUser member = newVerifiedUser();
        UUID projectId = createProject(owner.token());
        addMember(projectId, member.user());

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectId}/tags", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"JWT\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID tagId = UUID.fromString(json(created).get("id").asString());

        mockMvc.perform(patch("/api/v1/projects/{projectId}/tags/{tagId}", projectId, tagId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Security\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TAG_MANAGEMENT_FORBIDDEN"));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/tags/{tagId}", projectId, tagId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TAG_MANAGEMENT_FORBIDDEN"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/tags", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"jwt\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_NAME_ALREADY_EXISTS"));

        Project project = projectRepository.findById(projectId).orElseThrow();
        Tag tag = tagRepository.findByIdAndProjectId(tagId, projectId).orElseThrow();
        Document document = documentRepository.saveAndFlush(new Document(
                project, member.user(), "tagged.pdf", "tagged.pdf", FileKind.DOCUMENT,
                "pdf", "application/pdf", 100,
                "projects/%s/documents/%s.pdf".formatted(projectId, UUID.randomUUID())));
        documentTagRepository.saveAndFlush(new DocumentTag(
                new DocumentTagId(document.getId(), tag.getId()), projectId));

        mockMvc.perform(patch("/api/v1/projects/{projectId}/tags/{tagId}", projectId, tagId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Security\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/projects/{projectId}/tags/{tagId}", projectId, tagId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNoContent());
        assertThat(documentRepository.findById(document.getId())).isPresent();
        assertThat(documentTagRepository.existsByIdDocumentIdAndIdTagId(document.getId(), tagId))
                .isFalse();
    }

    private MvcResult createFolder(String token, UUID projectId, String name, UUID parentId)
            throws Exception {
        String parent = parentId == null ? "null" : "\"%s\"".formatted(parentId);
        return mockMvc.perform(post("/api/v1/projects/{projectId}/folders", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"parentId\":%s}".formatted(name, parent)))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
