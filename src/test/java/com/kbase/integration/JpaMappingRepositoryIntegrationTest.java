package com.kbase.integration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kbase.auth.entity.RefreshSession;
import com.kbase.auth.repository.RefreshSessionRepository;
import com.kbase.category.entity.Category;
import com.kbase.category.repository.CategoryRepository;
import com.kbase.document.entity.Document;
import com.kbase.document.entity.DocumentTag;
import com.kbase.document.enums.FileKind;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.document.repository.DocumentSpecification;
import com.kbase.document.repository.DocumentTagRepository;
import com.kbase.document.repository.DocumentStorageKeyProjection;
import com.kbase.folder.entity.Folder;
import com.kbase.folder.repository.FolderRepository;
import com.kbase.invitation.entity.ProjectInvitation;
import com.kbase.invitation.enums.InvitationStatus;
import com.kbase.invitation.repository.ProjectInvitationRepository;
import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.tag.entity.Tag;
import com.kbase.tag.repository.TagRepository;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA-11 repository/mapping gate. This suite intentionally uses real
 * PostgreSQL because partial/expression indexes and composite foreign keys
 * are part of the KBase persistence contract.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("local")
class JpaMappingRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(com.kbase.integration.support.PostgresTestSupport.IMAGE)
            .withDatabaseName("kbase")
            .withUsername("kbase")
            .withPassword("kbase");

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("kbase.postgres.host", POSTGRES::getHost);
        registry.add("kbase.postgres.port", () -> POSTGRES.getFirstMappedPort());
        registry.add("kbase.postgres.database", POSTGRES::getDatabaseName);
        registry.add("kbase.postgres.username", POSTGRES::getUsername);
        registry.add("kbase.postgres.password", POSTGRES::getPassword);
        registry.add("kbase.jwt.signing-secret", () -> "test-jwt-signing-secret-at-least-256-bits");
        registry.add("kbase.otp.hash-secret", () -> "test-otp-hash-secret");
        registry.add("kbase.mail.username", () -> "test@example.invalid");
        registry.add("kbase.mail.app-password", () -> "test-mail-app-password");
        registry.add("kbase.storage.access-key", () -> "test-access-key");
        registry.add("kbase.storage.secret-key", () -> "test-storage-secret-key");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectInvitationRepository invitationRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentTagRepository documentTagRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE "
                + "document_tags, documents, categories, folders, project_invitations, "
                + "project_members, refresh_sessions, tags, projects, users "
                + "RESTART IDENTITY CASCADE");
    }

    @Test
    void userVerificationAndStringEnumsMapToTheExistingColumns() {
        Instant verifiedAt = Instant.parse("2026-01-02T03:04:05Z");
        User user = userRepository.saveAndFlush(user("verified"));
        user.setEmailVerifiedAt(verifiedAt);
        userRepository.saveAndFlush(user);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT email, system_role, status, email_verified_at "
                        + "FROM users WHERE id = ?",
                user.getId());

        assertThat(row.get("email")).isEqualTo("verified@example.com");
        assertThat(row.get("system_role")).isEqualTo("USER");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("email_verified_at")).isNotNull();
        assertThat(userRepository.findByEmail("verified@example.com"))
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(user.getId()));
        assertThat(userRepository.existsByEmail("verified@example.com")).isTrue();
    }

    @Test
    @Transactional
    void refreshSessionsRemainPersistentAndUseLazyUserRelationship() throws Exception {
        User user = userRepository.saveAndFlush(user("session-user"));
        RefreshSession session = refreshSessionRepository.saveAndFlush(new RefreshSession(
                user, "refresh-hash-" + UUID.randomUUID(), Instant.now().plusSeconds(3600)));

        RefreshSession loaded = refreshSessionRepository.findByTokenHash(session.getTokenHash())
                .orElseThrow();
        ManyToOne userRelationship = RefreshSession.class.getDeclaredField("user")
                .getAnnotation(ManyToOne.class);
        assertThat(userRelationship.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(loaded.getUser().getId()).isEqualTo(user.getId());
        assertThat(refreshSessionRepository.findAllByUserId(user.getId())).hasSize(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long expiredDelete = transactionTemplate.execute(status ->
                refreshSessionRepository.deleteByExpiresAtBefore(Instant.now().minusSeconds(1)));
        assertThat(expiredDelete).isZero();
        Long userDelete = transactionTemplate.execute(status ->
                refreshSessionRepository.deleteByUserId(user.getId()));
        assertThat(userDelete).isEqualTo(1);
    }

    @Test
    void membershipQueriesAndDatabaseOwnerRulesAreEnforced() {
        User owner = userRepository.saveAndFlush(user("owner"));
        User member = userRepository.saveAndFlush(user("member"));
        Project project = projectRepository.saveAndFlush(project("membership-project"));

        ProjectMember ownerMembership = projectMemberRepository.saveAndFlush(
                new ProjectMember(project, owner, ProjectRole.OWNER));
        assertThat(projectMemberRepository.findByProjectIdAndUserId(project.getId(), owner.getId()))
                .hasValueSatisfying(found ->
                        assertThat(found.getId()).isEqualTo(ownerMembership.getId()));
        assertThat(projectMemberRepository.findByProjectIdAndRole(project.getId(), ProjectRole.OWNER))
                .hasValueSatisfying(found ->
                        assertThat(found.getId()).isEqualTo(ownerMembership.getId()));
        assertThat(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), owner.getId()))
                .isTrue();
        assertThat(projectMemberRepository.existsByUserId(owner.getId())).isTrue();
        assertThat(projectMemberRepository.existsByUserIdAndRole(owner.getId(), ProjectRole.OWNER))
                .isTrue();

        Page<ProjectMember> members = projectMemberRepository.findAllByProjectId(
                project.getId(), PageRequest.of(0, 10));
        assertThat(members).hasSize(1);
        assertThat(entityManagerFactory.getPersistenceUnitUtil()
                .isLoaded(members.getContent().getFirst(), "user")).isTrue();

        assertThatThrownBy(() -> projectMemberRepository.saveAndFlush(
                new ProjectMember(project, owner, ProjectRole.MEMBER)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_project_members_project_user");

        assertThatThrownBy(() -> projectMemberRepository.saveAndFlush(
                new ProjectMember(project, member, ProjectRole.OWNER)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_project_members_single_owner");

        ProjectMember memberMembership = projectMemberRepository.saveAndFlush(
                new ProjectMember(project, member, ProjectRole.MEMBER));
        assertThat(memberMembership.getRole()).isEqualTo(ProjectRole.MEMBER);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long memberDelete = transactionTemplate.execute(status ->
                projectMemberRepository.deleteByProjectIdAndUserId(project.getId(), member.getId()));
        assertThat(memberDelete).isEqualTo(1);
    }

    @Test
    void projectListingUsesMembershipInsteadOfAnOwnerColumn() {
        User user = userRepository.saveAndFlush(user("project-list-user"));
        Project first = projectRepository.saveAndFlush(project("first-project"));
        Project second = projectRepository.saveAndFlush(project("second-project"));
        projectMemberRepository.saveAndFlush(new ProjectMember(first, user, ProjectRole.OWNER));
        projectMemberRepository.saveAndFlush(new ProjectMember(second, user, ProjectRole.MEMBER));

        assertThat(projectMemberRepository.findProjectsForUser(
                user.getId(), null, PageRequest.of(0, 10)).getContent())
                .extracting(Project::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(projectMemberRepository.findProjectsForUser(
                user.getId(), ProjectRole.OWNER, PageRequest.of(0, 10)).getContent())
                .extracting(Project::getId)
                .containsExactly(first.getId());
    }

    @Test
    void invitationQueriesAndPartialPendingUniquenessWork() {
        User inviter = userRepository.saveAndFlush(user("inviter"));
        Project project = projectRepository.saveAndFlush(project("invitation-project"));
        Project anotherProject = projectRepository.saveAndFlush(project("another-project"));
        Instant expiry = Instant.now().plusSeconds(3600);

        ProjectInvitation pending = invitationRepository.saveAndFlush(new ProjectInvitation(
                project, inviter, "invitee@example.com", "token-hash-1", InvitationStatus.PENDING,
                expiry));
        assertThat(invitationRepository.findByTokenHash(pending.getTokenHash()))
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(pending.getId()));
        assertThat(invitationRepository.findByIdAndProjectId(pending.getId(), project.getId()))
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(pending.getId()));
        assertThat(invitationRepository.existsByProjectIdAndEmailAndStatus(
                project.getId(), "invitee@example.com", InvitationStatus.PENDING)).isTrue();
        assertThat(invitationRepository.existsByInvitedById(inviter.getId())).isTrue();

        assertThatThrownBy(() -> invitationRepository.saveAndFlush(new ProjectInvitation(
                project, inviter, "invitee@example.com", "token-hash-2", InvitationStatus.PENDING,
                expiry)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_project_pending_invitation_email");

        ProjectInvitation sameEmailOtherProject = invitationRepository.saveAndFlush(
                new ProjectInvitation(anotherProject, inviter, "invitee@example.com",
                        "token-hash-3", InvitationStatus.PENDING, expiry));
        assertThat(sameEmailOtherProject.getProject().getId()).isEqualTo(anotherProject.getId());

        ProjectInvitation accepted = new ProjectInvitation(
                project, inviter, "invitee@example.com", "token-hash-4", InvitationStatus.ACCEPTED,
                expiry);
        accepted.setAcceptedAt(Instant.now());
        invitationRepository.saveAndFlush(accepted);
        assertThat(invitationRepository.findAllByStatusAndExpiresAtBefore(
                InvitationStatus.PENDING, Instant.now().plusSeconds(7200))).hasSize(2);
        assertThat(invitationRepository.findAllByProjectIdAndStatus(
                project.getId(), InvitationStatus.PENDING, PageRequest.of(0, 10))).hasSize(1);
    }

    @Test
    @Transactional
    void invitationForUpdateUsesPessimisticWriteLock() throws Exception {
        User inviter = userRepository.saveAndFlush(user("lock-inviter"));
        Project project = projectRepository.saveAndFlush(project("lock-project"));
        ProjectInvitation invitation = invitationRepository.saveAndFlush(new ProjectInvitation(
                project, inviter, "lock@example.com", "lock-token-hash", InvitationStatus.PENDING,
                Instant.now().plusSeconds(3600)));

        ProjectInvitation locked = invitationRepository
                .findByTokenHashForUpdate(invitation.getTokenHash())
                .orElseThrow();
        assertThat(locked.getId()).isEqualTo(invitation.getId());
        assertThat(locked).isNotNull();
        // The repository annotation is the contract; the active transaction makes PostgreSQL
        // execute the SELECT ... FOR UPDATE instead of silently dropping the lock.
        Lock lock = ProjectInvitationRepository.class
                .getMethod("findByTokenHashForUpdate", String.class)
                .getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void folderHierarchyQueriesAndSameProjectIntegrityAreEnforced() {
        Project project = projectRepository.saveAndFlush(project("folder-project"));
        Project otherProject = projectRepository.saveAndFlush(project("other-folder-project"));

        Folder root = folderRepository.saveAndFlush(new Folder(project, null, "Root"));
        assertThat(folderRepository.findByIdAndProjectId(root.getId(), project.getId()))
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(root.getId()));
        assertThat(folderRepository.findAllByProjectId(project.getId())).hasSize(1);
        assertThat(folderRepository.existsByProjectIdAndParentIdIsNullAndNameIgnoreCase(
                project.getId(), "root")).isTrue();
        assertThat(folderRepository.existsSiblingWithNameExcluding(
                project.getId(), null, root.getId(), "root")).isFalse();

        assertThatThrownBy(() -> folderRepository.saveAndFlush(
                new Folder(project, null, "rOoT")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_folders_root_name");

        Folder secondParent = folderRepository.saveAndFlush(new Folder(project, null, "Second"));
        Folder firstChild = folderRepository.saveAndFlush(new Folder(project, root, "Specs"));
        assertThat(folderRepository.findAllByProjectIdAndParentId(project.getId(), root.getId()))
                .extracting(Folder::getId)
                .containsExactly(firstChild.getId());
        assertThat(folderRepository.existsByProjectIdAndParentIdAndNameIgnoreCase(
                project.getId(), root.getId(), "specs")).isTrue();
        assertThat(folderRepository.existsByParentId(root.getId())).isTrue();
        assertThat(folderRepository.existsSiblingWithNameExcluding(
                project.getId(), root.getId(), firstChild.getId(), "specs")).isFalse();
        folderRepository.saveAndFlush(new Folder(project, secondParent, "Specs"));

        Folder otherRoot = folderRepository.saveAndFlush(new Folder(otherProject, null, "Other"));
        assertThatThrownBy(() -> folderRepository.saveAndFlush(
                new Folder(project, otherRoot, "Cross project parent")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_folders_parent_same_project");

        Folder selfParent = folderRepository.saveAndFlush(new Folder(project, null, "Self"));
        selfParent.setParent(selfParent);
        assertThatThrownBy(() -> folderRepository.saveAndFlush(selfParent))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("ck_folders_not_self_parent");
    }

    @Test
    void categoryAndTagQueriesKeepCaseInsensitiveProjectScopedUniqueness() {
        Project project = projectRepository.saveAndFlush(project("organization-project"));
        Project otherProject = projectRepository.saveAndFlush(project("other-organization-project"));

        Category category = categoryRepository.saveAndFlush(new Category(project, "Security"));
        assertThat(categoryRepository.findByIdAndProjectId(category.getId(), project.getId()))
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(category.getId()));
        assertThat(categoryRepository.findAllByProjectIdOrderByNameAsc(project.getId()))
                .extracting(Category::getId)
                .containsExactly(category.getId());
        assertThat(categoryRepository.existsByProjectIdAndNameIgnoreCase(
                project.getId(), "security")).isTrue();
        assertThat(categoryRepository.existsByProjectIdAndNameIgnoreCaseAndIdNot(
                project.getId(), "security", category.getId())).isFalse();

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(
                new Category(project, "sEcUrItY")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_categories_project_name");
        categoryRepository.saveAndFlush(new Category(otherProject, "Security"));

        Tag firstTag = tagRepository.saveAndFlush(new Tag(project, "JWT"));
        Tag secondTag = tagRepository.saveAndFlush(new Tag(project, "Spring"));
        assertThat(tagRepository.findByIdAndProjectId(firstTag.getId(), project.getId()))
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(firstTag.getId()));
        assertThat(tagRepository.findAllByProjectIdOrderByNameAsc(project.getId()))
                .extracting(Tag::getName)
                .containsExactly("JWT", "Spring");
        assertThat(tagRepository.existsByProjectIdAndNameIgnoreCase(project.getId(), "jwt"))
                .isTrue();
        assertThatThrownBy(() -> tagRepository.saveAndFlush(new Tag(project, "jwt")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_tags_project_name");
        Tag otherProjectTag = tagRepository.saveAndFlush(new Tag(otherProject, "JWT"));

        assertThat(tagRepository.findAllByProjectIdAndIdIn(
                project.getId(), List.of(firstTag.getId(), secondTag.getId(), otherProjectTag.getId())))
                .extracting(Tag::getId)
                .containsExactlyInAnyOrder(firstTag.getId(), secondTag.getId());
    }

    @Test
    void documentMappingUsesUserUploaderAndCompositeSameProjectJoins() {
        User uploader = userRepository.saveAndFlush(user("uploader"));
        Project project = projectRepository.saveAndFlush(project("document-project"));
        Project otherProject = projectRepository.saveAndFlush(project("other-document-project"));
        Folder folder = folderRepository.saveAndFlush(new Folder(project, null, "Docs"));
        Category category = categoryRepository.saveAndFlush(new Category(project, "Guides"));
        Folder otherFolder = folderRepository.saveAndFlush(new Folder(otherProject, null, "Other docs"));
        Category otherCategory = categoryRepository.saveAndFlush(new Category(otherProject, "Other guides"));

        Document document = document(project, uploader, "Guide", "guide.pdf", "document/guide.pdf");
        document.setFolder(folder);
        document.setCategory(category);
        document.setDescription("A useful persistence guide");
        document = documentRepository.saveAndFlush(document);

        UUID documentId = document.getId();
        assertThat(documentRepository.findByIdAndProjectId(documentId, project.getId()))
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(documentId));
        assertThat(documentRepository.existsByFolderId(folder.getId())).isTrue();
        assertThat(documentRepository.existsByCategoryId(category.getId())).isTrue();
        assertThat(documentRepository.existsByUploadedById(uploader.getId())).isTrue();
        assertThat(documentRepository.countByProjectId(project.getId())).isEqualTo(1);

        List<DocumentStorageKeyProjection> keys = documentRepository.findStorageKeysByProjectId(
                project.getId());
        assertThat(keys).singleElement().satisfies(key -> {
            assertThat(key.getId()).isEqualTo(documentId);
            assertThat(key.getStorageKey()).isEqualTo("document/guide.pdf");
        });

        Document detail = documentRepository.findDetailById(documentId).orElseThrow();
        PersistenceUnitUtil persistence = entityManagerFactory.getPersistenceUnitUtil();
        assertThat(persistence.isLoaded(detail, "uploadedBy")).isTrue();
        assertThat(persistence.isLoaded(detail, "folder")).isTrue();
        assertThat(persistence.isLoaded(detail, "category")).isTrue();
        assertThat(detail.getUploadedBy().getId()).isEqualTo(uploader.getId());
        assertThat(detail.getFolder().getId()).isEqualTo(folder.getId());
        assertThat(detail.getCategory().getId()).isEqualTo(category.getId());

        Document crossFolder = document(project, uploader, "Cross folder", "cross-folder.pdf",
                "document/cross-folder.pdf");
        crossFolder.setFolder(otherFolder);
        assertThatThrownBy(() -> documentRepository.saveAndFlush(crossFolder))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_documents_folder_same_project");

        Document crossCategory = document(project, uploader, "Cross category", "cross-category.pdf",
                "document/cross-category.pdf");
        crossCategory.setCategory(otherCategory);
        assertThatThrownBy(() -> documentRepository.saveAndFlush(crossCategory))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_documents_category_same_project");

        assertThatThrownBy(() -> documentRepository.saveAndFlush(
                document(project, uploader, "Duplicate storage", "duplicate.pdf",
                        "document/guide.pdf")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_documents_storage_key");
    }

    @Test
    void documentTagsUseExplicitCompositeEntityAndDatabaseCascades() {
        User uploader = userRepository.saveAndFlush(user("tag-uploader"));
        Project project = projectRepository.saveAndFlush(project("tag-document-project"));
        Project otherProject = projectRepository.saveAndFlush(project("tag-other-project"));
        Tag tag = tagRepository.saveAndFlush(new Tag(project, "persistence"));
        Tag otherTag = tagRepository.saveAndFlush(new Tag(otherProject, "other"));
        Document document = documentRepository.saveAndFlush(
                document(project, uploader, "Tagged", "tagged.pdf", "document/tagged.pdf"));
        Document otherDocument = documentRepository.saveAndFlush(
                document(project, uploader, "Other tagged", "other-tagged.pdf",
                        "document/other-tagged.pdf"));

        DocumentTag link = new DocumentTag();
        link.setDocument(document);
        link.setTag(tag);
        link.setProjectId(project.getId());
        link = documentTagRepository.saveAndFlush(link);
        assertThat(link.getId().getDocumentId()).isEqualTo(document.getId());
        assertThat(link.getId().getTagId()).isEqualTo(tag.getId());
        assertThat(documentTagRepository.existsByIdDocumentIdAndIdTagId(
                document.getId(), tag.getId())).isTrue();
        assertThat(documentTagRepository.findAllByIdDocumentId(document.getId()))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.getProjectId()).isEqualTo(project.getId());
                    assertThat(entityManagerFactory.getPersistenceUnitUtil()
                            .isLoaded(found, "tag")).isTrue();
                    assertThat(found.getTag().getId()).isEqualTo(tag.getId());
                });

        assertThatThrownBy(() -> {
            DocumentTag duplicate = new DocumentTag();
            duplicate.setDocument(document);
            duplicate.setTag(tag);
            duplicate.setProjectId(project.getId());
            documentTagRepository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("pk_document_tags");

        assertThatThrownBy(() -> {
            DocumentTag crossProjectTag = new DocumentTag();
            crossProjectTag.setDocument(document);
            crossProjectTag.setTag(otherTag);
            crossProjectTag.setProjectId(project.getId());
            documentTagRepository.saveAndFlush(crossProjectTag);
        }).isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_document_tags_tag_same_project");

        DocumentTag secondLink = new DocumentTag();
        secondLink.setDocument(otherDocument);
        secondLink.setTag(tag);
        secondLink.setProjectId(project.getId());
        documentTagRepository.saveAndFlush(secondLink);

        documentRepository.deleteById(document.getId());
        documentRepository.flush();
        assertThat(documentTagRepository.findAllByIdDocumentId(document.getId())).isEmpty();

        tagRepository.deleteById(tag.getId());
        tagRepository.flush();
        assertThat(documentTagRepository.findAllByIdDocumentId(otherDocument.getId())).isEmpty();
        assertThat(documentRepository.findById(otherDocument.getId())).isPresent();
    }

    @Test
    void documentSpecificationAppliesMandatoryProjectAndAllMetadataFilters() {
        User uploader = userRepository.saveAndFlush(user("search-uploader"));
        Project project = projectRepository.saveAndFlush(project("search-project"));
        Project otherProject = projectRepository.saveAndFlush(project("search-other-project"));
        Folder folder = folderRepository.saveAndFlush(new Folder(project, null, "Search docs"));
        Category category = categoryRepository.saveAndFlush(new Category(project, "Security"));
        Tag tag = tagRepository.saveAndFlush(new Tag(project, "Spring"));
        Tag otherTag = tagRepository.saveAndFlush(new Tag(otherProject, "Spring"));

        Document matching = document(project, uploader, "Security Architecture", "architecture.pdf",
                "document/architecture.pdf");
        matching.setFolder(folder);
        matching.setCategory(category);
        matching.setDescription("Security and database guidance");
        matching.setCreatedAt(Instant.parse("2026-01-10T00:00:00Z"));
        matching.setUpdatedAt(Instant.parse("2026-01-10T00:00:00Z"));
        matching = documentRepository.saveAndFlush(matching);

        Document tagged = document(project, uploader, "Roadmap", "roadmap.md", "document/roadmap.md");
        tagged.setFileKind(FileKind.DOCUMENT);
        tagged.setCreatedAt(Instant.parse("2026-01-20T00:00:00Z"));
        tagged.setUpdatedAt(Instant.parse("2026-01-20T00:00:00Z"));
        tagged = documentRepository.saveAndFlush(tagged);

        Document other = document(otherProject, uploader, "Security Architecture", "other.pdf",
                "document/other.pdf");
        other = documentRepository.saveAndFlush(other);

        saveDocumentTag(matching, tag, project);
        saveDocumentTag(tagged, tag, project);
        saveDocumentTag(other, otherTag, otherProject);

        Page<Document> qResults = documentRepository.findAll(
                DocumentSpecification.search(project.getId(), "security", null, null, null,
                        null, null, null, null),
                PageRequest.of(0, 20));
        assertThat(qResults.getContent()).extracting(Document::getId).containsExactly(matching.getId());

        Page<Document> tagResults = documentRepository.findAll(
                DocumentSpecification.search(project.getId(), "roadmap", null, null, tag.getId(),
                        FileKind.DOCUMENT, uploader.getId(), Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-02-01T00:00:00Z")),
                PageRequest.of(0, 20));
        assertThat(tagResults.getContent()).extracting(Document::getId).containsExactly(tagged.getId());

        Page<Document> combined = documentRepository.findAll(
                DocumentSpecification.forProject(project.getId())
                        .and(DocumentSpecification.withFolderId(folder.getId()))
                        .and(DocumentSpecification.withCategoryId(category.getId()))
                        .and(DocumentSpecification.withTagId(tag.getId())),
                PageRequest.of(0, 20));
        assertThat(combined.getContent()).extracting(Document::getId).containsExactly(matching.getId());

        assertThatThrownBy(() -> DocumentSpecification.forProject(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("projectId is mandatory");
    }

    private User user(String localPart) {
        return new User(localPart + "@example.com", "password-hash", "Test " + localPart,
                SystemRole.USER, UserStatus.ACTIVE);
    }

    private Project project(String name) {
        return new Project(name, "Description for " + name);
    }

    private Document document(Project project, User uploader, String displayName,
            String originalFilename, String storageKey) {
        return new Document(project, uploader, displayName, originalFilename,
                FileKind.DOCUMENT, "pdf", "application/pdf", 128, storageKey);
    }

    private DocumentTag saveDocumentTag(Document document, Tag tag, Project project) {
        DocumentTag link = new DocumentTag();
        link.setDocument(document);
        link.setTag(tag);
        link.setProjectId(project.getId());
        return documentTagRepository.saveAndFlush(link);
    }
}
