package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kbase.category.entity.Category;
import com.kbase.category.repository.CategoryRepository;
import com.kbase.document.entity.Document;
import com.kbase.document.enums.FileKind;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.repository.DocumentTagRepository;
import com.kbase.folder.entity.Folder;
import com.kbase.folder.repository.FolderRepository;
import com.kbase.project.dto.request.CreateProjectRequest;
import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.project.service.ProjectService;
import com.kbase.security.jwt.JwtService;
import com.kbase.storage.exception.StorageObjectNotFoundException;
import com.kbase.storage.model.StorageUploadRequest;
import com.kbase.storage.service.StorageKeyFactory;
import com.kbase.storage.service.StorageService;
import com.kbase.tag.entity.Tag;
import com.kbase.tag.repository.TagRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * M11 API evidence through the real security filter chain, PostgreSQL and
 * MinIO. It deliberately exercises the public HTTP boundary rather than
 * calling the document service directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "spring.main.web-application-type=servlet", "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "kbase.jwt.signing-secret=m11-api-integration-jwt-signing-secret-256-bits!",
        "kbase.otp.hash-secret=m11-api-integration-otp-hash-secret",
        "kbase.mail.username=m11-api@example.invalid", "kbase.mail.app-password=m11-api-mail-password",
        "kbase.storage.access-key=minioadmin", "kbase.storage.secret-key=minioadmin",
        "kbase.storage.bucket=m11-api-documents", "kbase.storage.auto-create-bucket=false",
        "kbase.storage.initialize-on-startup=false", "kbase.upload.document-max-size=64B",
        "kbase.upload.max-batch-files=3"
})
class DocumentApiIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4\nM11".getBytes(StandardCharsets.US_ASCII);

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
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
                .makeBucket(MakeBucketArgs.builder().bucket("m11-api-documents").build());
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private ProjectService projectService;
    @Autowired private StorageService storage;
    @Autowired private StorageKeyFactory storageKeyFactory;
    @Autowired private UserRepository users;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectMemberRepository members;
    @Autowired private DocumentRepository documents;
    @Autowired private DocumentTagRepository documentTags;
    @Autowired private FolderRepository folders;
    @Autowired private CategoryRepository categories;
    @Autowired private TagRepository tags;

    @Test
    void uploadAndBatchValidateFilesAndSameProjectMetadataAtTheApiBoundary() throws Exception {
        User owner = user(SystemRole.USER, "Owner");
        User member = user(SystemRole.USER, "Member");
        Project project = project(owner, "upload");
        members.saveAndFlush(new ProjectMember(project, member, ProjectRole.MEMBER));
        Folder folder = folders.saveAndFlush(new Folder(project, null, "Docs"));
        Category category = categories.saveAndFlush(new Category(project, "Technical"));
        Tag tag = tags.saveAndFlush(new Tag(project, "m11"));
        Project otherProject = project(owner, "other");
        Folder otherFolder = folders.saveAndFlush(new Folder(otherProject, null, "Other"));
        Category otherCategory = categories.saveAndFlush(new Category(otherProject, "Other"));
        Tag otherTag = tags.saveAndFlush(new Tag(otherProject, "other"));

        MvcResult single = upload(project.getId(), token(member), "file", "proof.pdf", "application/pdf", PDF,
                "{\"displayName\":\"Proof.pdf\",\"folderId\":\"%s\",\"categoryId\":\"%s\",\"tagIds\":[\"%s\"]}"
                        .formatted(folder.getId(), category.getId(), tag.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Proof.pdf"))
                .andExpect(jsonPath("$.tags[0].id").value(tag.getId().toString()))
                .andReturn();
        UUID documentId = id(single);
        assertThat(single.getResponse().getContentAsString()).doesNotContain("storageKey");

        MockMultipartHttpServletRequestBuilder batch = multipart("/api/v1/projects/{id}/documents/batch", project.getId())
                .file(new MockMultipartFile("files", "first.pdf", "application/pdf", PDF))
                .file(new MockMultipartFile("files", "second.pdf", "application/pdf", PDF))
                .header(HttpHeaders.AUTHORIZATION, bearer(token(member)));
        mockMvc.perform(batch).andExpect(status().isCreated())
                .andExpect(jsonPath("$.documents.length()").value(2));

        long beforeRejectedBatch = documents.countByProjectId(project.getId());
        MockMultipartHttpServletRequestBuilder rejectedBatch = multipart("/api/v1/projects/{id}/documents/batch", project.getId())
                .file(new MockMultipartFile("files", "would-be-upload.pdf", "application/pdf", PDF))
                .file(new MockMultipartFile("files", "bad.exe", "application/octet-stream", new byte[] {1}))
                .header(HttpHeaders.AUTHORIZATION, bearer(token(member)));
        mockMvc.perform(rejectedBatch).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        assertThat(documents.countByProjectId(project.getId())).isEqualTo(beforeRejectedBatch);

        assertError(upload(project.getId(), token(member), "file", "empty.pdf", "application/pdf", new byte[0], null),
                400, "FILE_EMPTY");
        assertError(upload(project.getId(), token(member), "file", "malware.exe", "application/octet-stream",
                new byte[] {1}, null), 415, "UNSUPPORTED_FILE_TYPE");
        assertError(upload(project.getId(), token(member), "file", "notes.txt", "application/pdf",
                "text".getBytes(StandardCharsets.US_ASCII), null), 415, "MIME_TYPE_MISMATCH");
        assertError(upload(project.getId(), token(member), "file", "large.txt", "text/plain",
                new byte[65], null), 413, "FILE_TOO_LARGE");
        assertError(upload(project.getId(), token(member), "file", "cross.pdf", "application/pdf", PDF,
                "{\"folderId\":\"%s\"}".formatted(otherFolder.getId())), 404, "FOLDER_NOT_FOUND");
        assertError(upload(project.getId(), token(member), "file", "cross.pdf", "application/pdf", PDF,
                "{\"categoryId\":\"%s\"}".formatted(otherCategory.getId())), 404, "CATEGORY_NOT_FOUND");
        assertError(upload(project.getId(), token(member), "file", "cross.pdf", "application/pdf", PDF,
                "{\"tagIds\":[\"%s\"]}".formatted(otherTag.getId())), 404, "TAG_NOT_FOUND");

        assertThat(documents.findById(documentId)).isPresent();
        assertThat(documents.countByProjectId(project.getId())).isEqualTo(3);
    }

    @Test
    void readModifyDownloadAndDeleteHonorCurrentMembershipOwnershipOwnerAndAdmin() throws Exception {
        User owner = user(SystemRole.USER, "Owner");
        User member = user(SystemRole.USER, "Member");
        User admin = user(SystemRole.ADMIN, "Admin");
        Project project = project(owner, "permissions");
        members.saveAndFlush(new ProjectMember(project, member, ProjectRole.MEMBER));

        UUID documentId = id(upload(project.getId(), token(owner), "file", "owner.pdf", "application/pdf", PDF, null)
                .andExpect(status().isCreated()).andReturn());
        String stableKey = documents.findById(documentId).orElseThrow().getStorageKey();

        UUID ownDocumentId = id(upload(project.getId(), token(member), "file", "member.pdf", "application/pdf", PDF, null)
                .andExpect(status().isCreated()).andReturn());
        String ownKey = documents.findById(ownDocumentId).orElseThrow().getStorageKey();
        mockMvc.perform(patch("/api/v1/documents/{id}", ownDocumentId).header(HttpHeaders.AUTHORIZATION, bearer(token(member)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"member-renamed.pdf\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("member-renamed.pdf"));
        mockMvc.perform(delete("/api/v1/documents/{id}", ownDocumentId).header(HttpHeaders.AUTHORIZATION, bearer(token(member))))
                .andExpect(status().isNoContent());
        assertThat(documents.findById(ownDocumentId)).isEmpty();
        assertThatThrownBy(() -> storage.stat(ownKey)).isInstanceOf(StorageObjectNotFoundException.class);

        UUID ownerDeletedDocumentId = id(upload(project.getId(), token(member), "file", "owner-deletes.pdf", "application/pdf", PDF, null)
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(delete("/api/v1/documents/{id}", ownerDeletedDocumentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(owner))))
                .andExpect(status().isNoContent());
        assertThat(documents.findById(ownerDeletedDocumentId)).isEmpty();

        UUID formerDocumentId = id(upload(project.getId(), token(member), "file", "former.pdf", "application/pdf", PDF, null)
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(get("/api/v1/documents/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(token(member))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uploadedBy.id").value(owner.getId().toString()));
        mockMvc.perform(get("/api/v1/documents/{id}/download", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(member))))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("owner.pdf")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).containsExactly(PDF));

        mockMvc.perform(patch("/api/v1/documents/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(token(member)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"nope.pdf\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DOCUMENT_MODIFICATION_FORBIDDEN"));
        mockMvc.perform(patch("/api/v1/documents/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(token(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"renamed.pdf\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("renamed.pdf"));
        assertThat(documents.findById(documentId).orElseThrow().getStorageKey()).isEqualTo(stableKey);
        mockMvc.perform(patch("/api/v1/documents/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(token(admin)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"admin override\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("admin override"));

        mockMvc.perform(delete("/api/v1/projects/{projectId}/members/{userId}", project.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(owner))))
                .andExpect(status().isNoContent());
        assertThat(documents.findById(formerDocumentId)).isPresent();
        assertThat(documents.findById(formerDocumentId).orElseThrow().getUploadedBy().getId()).isEqualTo(member.getId());
        for (UUID inaccessibleId : List.of(documentId, formerDocumentId)) {
            for (String path : List.of("/api/v1/documents/" + inaccessibleId,
                    "/api/v1/documents/" + inaccessibleId + "/download")) {
                mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(token(member))))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_FORBIDDEN"));
            }
            mockMvc.perform(patch("/api/v1/documents/{id}", inaccessibleId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token(member)))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"nope.pdf\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROJECT_ACCESS_FORBIDDEN"));
            mockMvc.perform(delete("/api/v1/documents/{id}", inaccessibleId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token(member))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROJECT_ACCESS_FORBIDDEN"));
        }

        mockMvc.perform(delete("/api/v1/documents/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(token(admin))))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/documents/{id}", formerDocumentId).header(HttpHeaders.AUTHORIZATION, bearer(token(admin))))
                .andExpect(status().isNoContent());
        assertThat(documents.findById(documentId)).isEmpty();
        assertThat(documentTags.findAllByIdDocumentId(documentId)).isEmpty();
        assertThatThrownBy(() -> storage.stat(stableKey)).isInstanceOf(StorageObjectNotFoundException.class);
    }

    @Test
    void previewRangesAndProjectHardDeleteStreamAndCascadeAfterStorageSucceeds() throws Exception {
        User owner = user(SystemRole.USER, "Owner");
        User member = user(SystemRole.USER, "Member");
        User admin = user(SystemRole.ADMIN, "Admin");
        Project project = project(owner, "delete");
        members.saveAndFlush(new ProjectMember(project, member, ProjectRole.MEMBER));
        Folder folder = folders.saveAndFlush(new Folder(project, null, "Archive"));
        Category category = categories.saveAndFlush(new Category(project, "Video"));
        Tag tag = tags.saveAndFlush(new Tag(project, "range"));

        byte[] videoBytes = "0123456789".getBytes(StandardCharsets.US_ASCII);
        Document video = storedDocument(project, owner, folder, category, "clip.mp4", "mp4", "video/mp4", videoBytes);
        Document office = storedDocument(project, owner, null, null, "report.docx", "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {1});

        mockMvc.perform(get("/api/v1/documents/{id}/preview", video.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(member))).header(HttpHeaders.RANGE, "bytes=2-4"))
                .andExpect(status().isPartialContent()).andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-4/10"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .containsExactly("234".getBytes(StandardCharsets.US_ASCII)));
        mockMvc.perform(get("/api/v1/documents/{id}/preview", video.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(member))).header(HttpHeaders.RANGE, "bytes=99-100"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */10"));
        mockMvc.perform(get("/api/v1/documents/{id}/preview", office.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(member))))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("PREVIEW_NOT_SUPPORTED"));

        mockMvc.perform(delete("/api/v1/projects/{id}", project.getId()).header(HttpHeaders.AUTHORIZATION, bearer(token(member))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROJECT_MANAGEMENT_FORBIDDEN"));
        mockMvc.perform(delete("/api/v1/projects/{id}", project.getId()).header(HttpHeaders.AUTHORIZATION, bearer(token(admin))))
                .andExpect(status().isNoContent());
        assertThat(projects.findById(project.getId())).isEmpty();
        assertThat(documents.findById(video.getId())).isEmpty();
        assertThat(documents.findById(office.getId())).isEmpty();
        assertThat(members.findAllByProjectId(project.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(folders.findAllByProjectId(project.getId())).isEmpty();
        assertThat(categories.findAllByProjectIdOrderByNameAsc(project.getId())).isEmpty();
        assertThat(tags.findAllByProjectIdOrderByNameAsc(project.getId())).isEmpty();
        assertThatThrownBy(() -> storage.stat(video.getStorageKey())).isInstanceOf(StorageObjectNotFoundException.class);
        assertThatThrownBy(() -> storage.stat(office.getStorageKey())).isInstanceOf(StorageObjectNotFoundException.class);
    }

    private Document storedDocument(Project project, User uploader, Folder folder, Category category, String name,
            String extension, String mimeType, byte[] contents) {
        UUID documentId = UUID.randomUUID();
        String key = storageKeyFactory.documentObjectKey(project.getId(), documentId, extension);
        storage.upload(new StorageUploadRequest(key, new ByteArrayInputStream(contents), contents.length, mimeType));
        Document document = new Document(project, uploader, name, name, extension.equals("mp4") ? FileKind.VIDEO : FileKind.DOCUMENT,
                extension, mimeType, contents.length, key);
        document.setId(documentId);
        document.setFolder(folder);
        document.setCategory(category);
        return documents.saveAndFlush(document);
    }

    private User user(SystemRole role, String name) {
        User user = new User(name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com", "hash", name,
                role, UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private Project project(User owner, String name) {
        UUID id = projectService.createProject(owner.getId(), new CreateProjectRequest("M11 " + name + " " + UUID.randomUUID(), null)).id();
        return projects.findById(id).orElseThrow();
    }

    private org.springframework.test.web.servlet.ResultActions upload(UUID projectId, String token, String part,
            String filename, String contentType, byte[] contents, String metadata) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/projects/{id}/documents", projectId)
                .file(new MockMultipartFile(part, filename, contentType, contents))
                .header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (metadata != null) {
            request.file(new MockMultipartFile("metadata", "metadata.json", MediaType.APPLICATION_JSON_VALUE,
                    metadata.getBytes(StandardCharsets.UTF_8)));
        }
        return mockMvc.perform(request);
    }

    private void assertError(org.springframework.test.web.servlet.ResultActions action, int expectedStatus, String code)
            throws Exception {
        action.andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.code").value(code));
    }

    private UUID id(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return UUID.fromString(json.get("id").asString());
    }

    private String token(User user) {
        return jwtService.generateAccessToken(user).token();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
