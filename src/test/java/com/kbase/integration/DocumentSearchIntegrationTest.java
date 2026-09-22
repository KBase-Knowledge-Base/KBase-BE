package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kbase.category.entity.Category;
import com.kbase.category.repository.CategoryRepository;
import com.kbase.document.entity.Document;
import com.kbase.document.entity.DocumentTag;
import com.kbase.document.enums.FileKind;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.repository.DocumentTagRepository;
import com.kbase.folder.entity.Folder;
import com.kbase.folder.repository.FolderRepository;
import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.security.jwt.JwtService;
import com.kbase.tag.entity.Tag;
import com.kbase.tag.repository.TagRepository;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * M12 HTTP contract and isolation matrix through the real security chain and
 * a Flyway/Hibernate-validated PostgreSQL Testcontainer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "spring.main.web-application-type=servlet", "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "kbase.jwt.signing-secret=m12-search-integration-jwt-signing-secret-256-bits!",
        "kbase.otp.hash-secret=m12-search-integration-otp-hash-secret",
        "kbase.mail.username=m12-search@example.invalid", "kbase.mail.app-password=m12-search-password",
        "kbase.storage.access-key=m12-search-access-key", "kbase.storage.secret-key=m12-search-secret-key"
})
class DocumentSearchIntegrationTest {

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(com.kbase.integration.support.PostgresTestSupport.IMAGE);

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository users;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectMemberRepository members;
    @Autowired private DocumentRepository documents;
    @Autowired private DocumentTagRepository documentTags;
    @Autowired private FolderRepository folders;
    @Autowired private CategoryRepository categories;
    @Autowired private TagRepository tags;

    @Test
    void searchesEveryApprovedMetadataFieldAndAllFiltersWithoutDuplicatesOrCrossProjectLeaks() throws Exception {
        User owner = user(SystemRole.USER, "search-owner");
        User uploader = user(SystemRole.USER, "search-uploader");
        Project projectA = project("search-a", owner);
        members.saveAndFlush(new ProjectMember(projectA, uploader, ProjectRole.MEMBER));
        Folder folder = folders.saveAndFlush(new Folder(projectA, null, "Search Folder"));
        Category category = categories.saveAndFlush(new Category(projectA, "Metadata Category"));
        Tag matchingTag = tags.saveAndFlush(new Tag(projectA, "metadata-tag"));
        Tag secondMatchingTag = tags.saveAndFlush(new Tag(projectA, "metadata-extra"));

        Document primary = document(projectA, uploader, "display-needle", "original-needle.pdf",
                FileKind.DOCUMENT, "description-needle", Instant.parse("2026-02-01T00:00:00Z"), folder, category);
        link(primary, matchingTag);
        link(primary, secondMatchingTag);

        Project projectB = project("search-b", owner);
        Category categoryB = categories.saveAndFlush(new Category(projectB, "Metadata Category"));
        Tag tagB = tags.saveAndFlush(new Tag(projectB, "metadata-tag"));
        Document sameMetadataInOtherProject = document(projectB, uploader, "display-needle", "original-needle.pdf",
                FileKind.DOCUMENT, "description-needle", Instant.parse("2026-02-01T00:00:00Z"), null, categoryB);
        link(sameMetadataInOtherProject, tagB);

        for (String q : List.of("display-needle", "original-needle", "description-needle",
                "metadata category", "metadata-tag")) {
            MvcResult result = search(projectA, owner, "q", q);
            assertThat(ids(result)).containsExactly(primary.getId());
            assertThat(json(result).get("totalElements").asLong()).isEqualTo(1);
        }
        // Both tags match this query. EXISTS, rather than a collection join, must still return one row.
        MvcResult duplicateGuard = search(projectA, owner, "q", "metadata");
        assertThat(ids(duplicateGuard)).containsExactly(primary.getId());
        assertThat(json(duplicateGuard).get("totalElements").asLong()).isEqualTo(1);

        assertThat(ids(search(projectA, owner, "folderId", folder.getId().toString())))
                .containsExactly(primary.getId());
        assertThat(ids(search(projectA, owner, "categoryId", category.getId().toString())))
                .containsExactly(primary.getId());
        assertThat(ids(search(projectA, owner, "tagId", matchingTag.getId().toString())))
                .containsExactly(primary.getId());
        assertThat(ids(search(projectA, owner, "fileKind", "DOCUMENT"))).containsExactly(primary.getId());
        assertThat(ids(search(projectA, owner, "uploadedBy", uploader.getId().toString())))
                .containsExactly(primary.getId());
        assertThat(ids(search(projectA, owner, "createdFrom", "2026-02-01T00:00:00Z",
                "createdTo", "2026-02-01T00:00:00Z"))).containsExactly(primary.getId());
        assertThat(ids(search(projectA, owner,
                "q", "display-needle", "folderId", folder.getId().toString(),
                "categoryId", category.getId().toString(), "tagId", matchingTag.getId().toString(),
                "fileKind", "DOCUMENT", "uploadedBy", uploader.getId().toString(),
                "createdFrom", "2026-01-31T00:00:00Z", "createdTo", "2026-02-02T00:00:00Z")))
                .containsExactly(primary.getId());
    }

    @Test
    void queryTreatsLikeWildcardsAsLiteralText() throws Exception {
        User owner = user(SystemRole.USER, "wildcard-owner");
        Project project = project("wildcard", owner);
        Document percent = document(project, owner, "100% plan", "100% plan.pdf",
                FileKind.DOCUMENT, "a_b description", Instant.parse("2026-04-01T00:00:00Z"), null, null);
        document(project, owner, "plain plan", "plain.pdf",
                FileKind.DOCUMENT, "ab description", Instant.parse("2026-04-01T00:00:00Z"), null, null);
        tags.saveAndFlush(new Tag(project, "hot_tag"));

        // "%" and "_" must match literally, not act as SQL wildcards: the
        // percent document is the only one containing either character.
        assertThat(ids(search(project, owner, "q", "%"))).containsExactly(percent.getId());
        assertThat(ids(search(project, owner, "q", "a_b"))).containsExactly(percent.getId());
        assertThat(ids(search(project, owner, "q", "_"))).containsExactly(percent.getId());
        // Without escaping, "p_a%n" would match "plain plan" as a wildcard pattern.
        assertThat(ids(search(project, owner, "q", "p_a%n"))).isEmpty();

        assertThat(json(search(project, owner, "q", "a_b")).get("totalElements").asLong()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/projects").param("q", "%")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        // "hot_tag" contains a literal underscore, so q="_" matches exactly it;
        // q="%" matches nothing because no tag name contains a literal percent.
        mockMvc.perform(get("/api/v1/projects/{projectId}/tags", project.getId()).param("q", "_")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("hot_tag"));
        mockMvc.perform(get("/api/v1/projects/{projectId}/tags", project.getId()).param("q", "%")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void enforcesPaginationSortWhitelistAndMembershipWithAdminOverride() throws Exception {
        User owner = user(SystemRole.USER, "page-owner");
        User member = user(SystemRole.USER, "page-member");
        User outsider = user(SystemRole.USER, "page-outsider");
        User admin = user(SystemRole.ADMIN, "page-admin");
        Project project = project("pagination", owner);
        members.saveAndFlush(new ProjectMember(project, member, ProjectRole.MEMBER));
        for (int i = 0; i < 21; i++) {
            document(project, owner, "page-%02d".formatted(i), "page-%02d.pdf".formatted(i),
                    FileKind.DOCUMENT, "pagination-group", Instant.parse("2026-03-01T00:00:00Z").plusSeconds(i),
                    null, null);
        }

        MvcResult defaults = search(project, owner, "q", "pagination-group");
        assertThat(json(defaults).get("page").asInt()).isZero();
        assertThat(json(defaults).get("size").asInt()).isEqualTo(20);
        assertThat(json(defaults).get("totalElements").asLong()).isEqualTo(21);
        assertThat(json(defaults).get("content").size()).isEqualTo(20);
        assertThat(json(search(project, owner, "q", "pagination-group", "page", "1", "size", "20"))
                .get("content").size()).isEqualTo(1);
        assertThat(json(search(project, owner, "q", "pagination-group", "size", "999"))
                .get("size").asInt()).isEqualTo(100);

        assertThat(names(search(project, owner, "q", "pagination-group", "sort", "displayName,asc")))
                .startsWith("page-00", "page-01");
        assertThat(names(search(project, owner, "q", "pagination-group", "sort", "displayName,desc")))
                .startsWith("page-20", "page-19");
        assertThat(names(search(project, owner, "q", "pagination-group", "sort", "DISPLAYNAME,desc")))
                .startsWith("page-20", "page-19");
        mockMvc.perform(get("/api/v1/projects/{projectId}/documents", project.getId())
                        .param("sort", "storageKey,desc").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        for (User permitted : List.of(member, owner, admin)) {
            mockMvc.perform(get("/api/v1/projects/{projectId}/documents", project.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(permitted)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/projects/{projectId}/documents", project.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROJECT_ACCESS_FORBIDDEN"));
    }

    private User user(SystemRole role, String prefix) {
        User user = new User(prefix + "-" + UUID.randomUUID() + "@example.com", "test-password-hash",
                prefix, role, UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private Project project(String name, User owner) {
        Project project = projects.saveAndFlush(new Project(name, "M12 search test"));
        members.saveAndFlush(new ProjectMember(project, owner, ProjectRole.OWNER));
        return project;
    }

    private Document document(Project project, User uploader, String displayName, String originalFilename,
            FileKind fileKind, String description, Instant createdAt, Folder folder, Category category) {
        Document document = new Document(project, uploader, displayName, originalFilename, fileKind,
                "pdf", "application/pdf", 42, "m12/" + UUID.randomUUID());
        document.setDescription(description);
        document.setCreatedAt(createdAt);
        document.setUpdatedAt(createdAt);
        document.setFolder(folder);
        document.setCategory(category);
        return documents.saveAndFlush(document);
    }

    private void link(Document document, Tag tag) {
        DocumentTag relation = new DocumentTag();
        relation.setDocument(document);
        relation.setTag(tag);
        relation.setProjectId(document.getProject().getId());
        documentTags.saveAndFlush(relation);
    }

    private MvcResult search(Project project, User user, String... parameters) throws Exception {
        var request = get("/api/v1/projects/{projectId}/documents", project.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user));
        for (int index = 0; index < parameters.length; index += 2) {
            request.param(parameters[index], parameters[index + 1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user).token();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<UUID> ids(MvcResult result) throws Exception {
        return json(result).get("content").valueStream()
                .map(node -> UUID.fromString(node.get("id").asString())).toList();
    }

    private List<String> names(MvcResult result) throws Exception {
        return json(result).get("content").valueStream()
                .map(node -> node.get("displayName").asString()).toList();
    }
}
