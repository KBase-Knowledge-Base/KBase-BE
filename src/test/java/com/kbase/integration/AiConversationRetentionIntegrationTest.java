package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kbase.ai.config.AiProperties;
import com.kbase.ai.entity.AiJob;
import com.kbase.ai.enums.AiJobStatus;
import com.kbase.ai.enums.AiJobType;
import com.kbase.ai.job.AiJobClaim;
import com.kbase.ai.repository.AiJobRepository;
import com.kbase.ai.service.AiConversationRetentionService;
import com.kbase.ai.service.AiJobStore;
import com.kbase.invitation.entity.ProjectInvitation;
import com.kbase.invitation.enums.InvitationStatus;
import com.kbase.invitation.repository.ProjectInvitationRepository;
import com.kbase.invitation.service.InvitationService;
import com.kbase.invitation.service.InvitationTokens;
import com.kbase.project.dto.request.CreateProjectRequest;
import com.kbase.project.entity.Project;
import com.kbase.project.entity.ProjectMember;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.project.repository.ProjectRepository;
import com.kbase.project.service.ProjectMemberService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL evidence for membership-loss retention scheduling and rejoin neutralization. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("local")
class AiConversationRetentionIntegrationTest {

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
        registry.add("kbase.jwt.signing-secret", () -> "m3-retention-jwt-signing-secret-at-least-256-bits");
        registry.add("kbase.otp.hash-secret", () -> "m3-retention-otp-secret");
        registry.add("kbase.mail.username", () -> "m3-retention@example.invalid");
        registry.add("kbase.mail.app-password", () -> "m3-retention-mail-password");
        registry.add("kbase.storage.access-key", () -> "m3-retention-access-key");
        registry.add("kbase.storage.secret-key", () -> "m3-retention-storage-secret");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
    }

    @MockitoBean
    private StorageService storageService;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private AiConversationRetentionService retentionService;

    @Autowired
    private AiJobStore jobStore;

    @Autowired
    private AiJobRepository jobRepository;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectInvitationRepository invitationRepository;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private InvitationTokens invitationTokens;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearJobs() {
        jdbcTemplate.update("DELETE FROM ai_jobs");
    }

    @Test
    void removeMemberSchedulesOnePurgeAtSevenDaysAndRepeatedEventDeduplicates() {
        User owner = user("remove-owner");
        User member = user("remove-member");
        Project project = project(owner, "remove-member");
        addMember(project, member);
        Instant before = Instant.now();

        projectMemberService.removeMember(project.getId(), member.getId(), principal(owner));

        Instant after = Instant.now();
        AiJob job = onlyPurge(project, member);
        assertThat(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), member.getId()))
                .isFalse();
        assertThat(job.getJobType()).isEqualTo(AiJobType.CONVERSATION_PURGE);
        assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING);
        assertThat(job.getDedupKey()).isEqualTo(
                "conversation-purge:" + project.getId() + ":" + member.getId());
        assertThat(job.getRunAt()).isBetween(
                before.plus(aiProperties.getRetention()), after.plus(aiProperties.getRetention()));

        retentionService.schedulePurge(project.getId(), member.getId(), before);
        assertThat(jobRepository.findAllByProjectId(project.getId())).hasSize(1);
    }

    @Test
    void leaveProjectSchedulesPurgeAtSevenDays() {
        User owner = user("leave-owner");
        User member = user("leave-member");
        Project project = project(owner, "leave-member");
        addMember(project, member);
        Instant before = Instant.now();

        projectMemberService.leaveProject(project.getId(), principal(member));

        Instant after = Instant.now();
        AiJob job = onlyPurge(project, member);
        assertThat(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), member.getId()))
                .isFalse();
        assertThat(job.getRunAt()).isBetween(
                before.plus(aiProperties.getRetention()), after.plus(aiProperties.getRetention()));
    }

    @Test
    void rejoinCancelsPendingPurgeAndLaterLossCreatesANewFutureJob() {
        User owner = user("rejoin-owner");
        User member = user("rejoin-member");
        Project project = project(owner, "rejoin-member");
        addMember(project, member);

        projectMemberService.removeMember(project.getId(), member.getId(), principal(owner));
        AiJob first = onlyPurge(project, member);
        String token = "rejoin-token-" + UUID.randomUUID();
        invitationRepository.saveAndFlush(new ProjectInvitation(
                project, owner, member.getEmail(), invitationTokens.hash(token),
                InvitationStatus.PENDING, Instant.now().plus(Duration.ofDays(1))));

        invitationService.accept(token, principal(member));

        assertThat(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), member.getId()))
                .isTrue();
        assertThat(jobRepository.findById(first.getId())).hasValueSatisfying(job ->
                assertThat(job.getStatus()).isEqualTo(AiJobStatus.CANCELLED));

        projectMemberService.leaveProject(project.getId(), principal(member));

        List<AiJob> jobs = jobRepository.findAllByProjectId(project.getId());
        assertThat(jobs).hasSize(2);
        assertThat(jobs).filteredOn(job -> job.getStatus() == AiJobStatus.PENDING)
                .singleElement().satisfies(job -> {
                    assertThat(job.getId()).isNotEqualTo(first.getId());
                    assertThat(job.getRunAt()).isAfter(first.getRunAt());
                });
    }

    @Test
    void projectDeleteCascadesRetentionJobAndRejectsLateClaimedWorker() {
        User owner = user("retention-delete-owner");
        User member = user("retention-delete-member");
        Project project = project(owner, "retention-delete");
        addMember(project, member);
        projectMemberService.removeMember(project.getId(), member.getId(), principal(owner));
        AiJob job = onlyPurge(project, member);
        AiJobClaim claim = jobStore.claimDueJobs(Set.of(AiJobType.CONVERSATION_PURGE), 1,
                job.getRunAt().plusSeconds(1)).getFirst();

        projectService.deleteProject(project.getId(), principal(owner));

        assertThat(projectRepository.findById(project.getId())).isEmpty();
        assertThat(jobRepository.findAllByProjectId(project.getId())).isEmpty();
        assertThat(jobStore.markDone(claim)).isFalse();
    }

    private AiJob onlyPurge(Project project, User user) {
        return jobRepository.findAllByProjectId(project.getId()).stream()
                .filter(job -> job.getJobType() == AiJobType.CONVERSATION_PURGE)
                .filter(job -> user.getId().equals(job.getUserId()))
                .findFirst().orElseThrow();
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

    private ProjectMember addMember(Project project, User user) {
        return projectMemberRepository.saveAndFlush(new ProjectMember(project, user, ProjectRole.MEMBER));
    }

    private static CustomUserPrincipal principal(User user) {
        return new CustomUserPrincipal(user.getId(), user.getEmail(),
                SystemRole.USER, UserStatus.ACTIVE, true);
    }
}
