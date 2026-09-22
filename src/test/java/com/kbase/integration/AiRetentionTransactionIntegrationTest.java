package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.kbase.ai.job.AiJobEnqueueResult;
import com.kbase.ai.job.AiJobSchedule;
import com.kbase.ai.service.AiJobStore;
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies membership mutation and retention scheduling share one rollback boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("local")
class AiRetentionTransactionIntegrationTest {

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
        registry.add("kbase.postgres.database", POSTGRES::getDatabaseName);
        registry.add("kbase.postgres.username", POSTGRES::getUsername);
        registry.add("kbase.postgres.password", POSTGRES::getPassword);
        registry.add("kbase.jwt.signing-secret", () -> "m3-retention-rollback-jwt-signing-secret-at-least-256-bits");
        registry.add("kbase.otp.hash-secret", () -> "m3-retention-rollback-otp-secret");
        registry.add("kbase.mail.username", () -> "m3-retention-rollback@example.invalid");
        registry.add("kbase.mail.app-password", () -> "m3-retention-rollback-mail-password");
        registry.add("kbase.storage.access-key", () -> "m3-retention-rollback-access-key");
        registry.add("kbase.storage.secret-key", () -> "m3-retention-rollback-storage-secret");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
    }

    @MockitoBean
    private AiJobStore jobStore;

    @MockitoBean
    private StorageService storageService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void failedRetentionEnqueueRollsBackMemberRemoval() {
        User owner = user("rollback-owner");
        User member = user("rollback-member");
        Project project = project(owner);
        projectMemberRepository.saveAndFlush(new ProjectMember(project, member, ProjectRole.MEMBER));
        when(jobStore.enqueueActive(any(AiJobSchedule.class)))
                .thenThrow(new IllegalStateException("retention persistence failure"));

        assertThatThrownBy(() -> projectMemberService.removeMember(
                project.getId(), member.getId(), principal(owner)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(projectMemberRepository.findByProjectIdAndUserId(project.getId(), member.getId()))
                .isPresent();
    }

    private User user(String localPart) {
        User user = new User(localPart + "-" + UUID.randomUUID() + "@example.com",
                "hash", localPart, SystemRole.USER, UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private Project project(User owner) {
        UUID projectId = projectService.createProject(owner.getId(),
                new CreateProjectRequest("retention-rollback-" + UUID.randomUUID(), null)).id();
        return projectRepository.findById(projectId).orElseThrow();
    }

    private static CustomUserPrincipal principal(User user) {
        return new CustomUserPrincipal(user.getId(), user.getEmail(),
                SystemRole.USER, UserStatus.ACTIVE, true);
    }
}
