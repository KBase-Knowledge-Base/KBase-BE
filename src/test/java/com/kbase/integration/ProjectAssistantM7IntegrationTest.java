package com.kbase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.kbase.ai.dto.response.AiTurnResponse;
import com.kbase.ai.enums.AiGenerationStatus;
import com.kbase.ai.provider.error.AiProviderErrorCategory;
import com.kbase.ai.provider.error.AiProviderException;
import com.kbase.ai.provider.fake.FakeAiChatModel;
import com.kbase.ai.provider.fake.FakeAiEmbeddingModel;
import com.kbase.ai.provider.model.AiChatResult;
import com.kbase.ai.provider.port.AiChatModel;
import com.kbase.ai.provider.port.AiEmbeddingModel;
import com.kbase.ai.repository.AiVectorRepository;
import com.kbase.ai.repository.DocumentAiChunkInsert;
import com.kbase.ai.service.ProjectAssistantConversationService;
import com.kbase.ai.service.ProjectAssistantFinalizationHook;
import com.kbase.security.jwt.JwtService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.storage.service.StorageService;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** M7 HTTP, privacy, transaction and source lifecycle evidence on real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class ProjectAssistantM7IntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            com.kbase.integration.support.PostgresTestSupport.IMAGE)
            .withDatabaseName("kbase").withUsername("kbase").withPassword("kbase");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("kbase.postgres.host", POSTGRES::getHost);
        registry.add("kbase.postgres.port", () -> POSTGRES.getFirstMappedPort());
        registry.add("kbase.postgres.database", POSTGRES::getDatabaseName);
        registry.add("kbase.postgres.username", POSTGRES::getUsername);
        registry.add("kbase.postgres.password", POSTGRES::getPassword);
        registry.add("kbase.jwt.signing-secret", () -> "m7-jwt-signing-secret-at-least-256-bits-long");
        registry.add("kbase.otp.hash-secret", () -> "m7-otp-hash-secret");
        registry.add("kbase.mail.username", () -> "m7@example.invalid");
        registry.add("kbase.mail.app-password", () -> "m7-mail-test-password");
        registry.add("kbase.storage.access-key", () -> "m7-storage-test-key");
        registry.add("kbase.storage.secret-key", () -> "m7-storage-test-secret");
        registry.add("kbase.storage.initialize-on-startup", () -> false);
        registry.add("kbase.ai.enabled", () -> true);
        registry.add("kbase.ai.gemini.api-key", () -> "m7-synthetic-never-used-test-key");
        registry.add("kbase.ai.worker.poll-interval", () -> "PT1H");
    }

    @MockitoBean(name = "aiChatModel")
    private AiChatModel chatModel;

    @MockitoBean(name = "aiEmbeddingModel")
    private AiEmbeddingModel embeddingModel;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private ProjectAssistantFinalizationHook finalizationHook;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private ProjectAssistantConversationService conversations;
    @Autowired private AiVectorRepository vectors;

    private final FakeAiChatModel fakeChat = new FakeAiChatModel(
            new AiChatResult("A verified answer [SOURCE_1]", "fake-chat"));
    private final FakeAiEmbeddingModel fakeEmbedding = new FakeAiEmbeddingModel();

    @BeforeEach
    void useDeterministicProviderPorts() {
        fakeChat.reset();
        fakeChat.withResponse(new AiChatResult("A verified answer [SOURCE_1]", "fake-chat"));
        fakeEmbedding.reset();
        when(chatModel.generate(any())).thenAnswer(invocation ->
                fakeChat.generate(invocation.getArgument(0)));
        when(embeddingModel.embed(any())).thenAnswer(invocation ->
                fakeEmbedding.embed(invocation.getArgument(0)));
    }

    @Test
    void createNoEvidencePersistsFirstTurnAndJwtIsRequired() throws Exception {
        Fixture fixture = fixture();
        String path = conversationsPath(fixture.projectId());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Why?\"}"))
                .andExpect(status().isUnauthorized());

        String body = mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  Why?  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversation.title").value("Why?"))
                .andExpect(jsonPath("$.message.answerType").value("NO_EVIDENCE"))
                .andExpect(jsonPath("$.message.generationStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.sources.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(fakeChat.invocationCount()).isZero();
        assertThat(fakeEmbedding.invocationCount()).isEqualTo(1);
        UUID id = UUID.fromString(new tools.jackson.databind.ObjectMapper()
                .readTree(body).get("conversation").get("id").asString());
        assertThat(count("ai_messages", "conversation_id", id)).isEqualTo(2);

        mvc.perform(get(path + "/" + id + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void ownershipIsPrivateForMemberOwnerAndAdmin() throws Exception {
        Fixture fixture = fixture();
        User member = user(SystemRole.USER);
        User admin = user(SystemRole.ADMIN);
        membership(fixture.projectId(), member, "MEMBER");
        var own = conversations.create(fixture.projectId(), principal(member), "Member question");
        UUID id = own.conversation().id();
        String path = conversationsPath(fixture.projectId()) + "/" + id;

        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_CONVERSATION_NOT_FOUND"));
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_CONVERSATION_NOT_FOUND"));
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk());
        mvc.perform(get(conversationsPath(fixture.projectId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        assertThat(conversations.create(fixture.projectId(), principal(admin), "Admin own")
                .conversation().id()).isNotEqualTo(id);

        jdbc.update("DELETE FROM project_members WHERE project_id = ? AND user_id = ?",
                fixture.projectId(), member.getId());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_FORBIDDEN"));
        assertThat(count("ai_conversations", "id", id)).isEqualTo(1);
    }

    @Test
    void lockedQuotaAllowsOnlyTheFifthConcurrentCreateAndDeleteFreesQuota() throws Exception {
        Fixture fixture = fixture();
        CustomUserPrincipal principal = principal(fixture.owner());
        for (int i = 0; i < 4; i++) {
            conversations.create(fixture.projectId(), principal, "Question " + i);
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<Object> first = pool.submit(() -> concurrentCreate(fixture.projectId(), principal, ready, go));
            Future<Object> second = pool.submit(() -> concurrentCreate(fixture.projectId(), principal, ready, go));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            Object a = first.get(30, TimeUnit.SECONDS);
            Object b = second.get(30, TimeUnit.SECONDS);
            assertThat(java.util.List.of(a, b)).filteredOn(value ->
                    value instanceof BusinessException exception
                            && exception.getErrorCode() == ErrorCode.AI_CONVERSATION_LIMIT_REACHED)
                    .hasSize(1);
        }
        assertThat(countPair(fixture.projectId(), fixture.owner().getId())).isEqualTo(5);
        UUID toDelete = jdbc.queryForObject("SELECT id FROM ai_conversations WHERE project_id = ? "
                + "AND created_by_user_id = ? LIMIT 1", UUID.class,
                fixture.projectId(), fixture.owner().getId());
        conversations.delete(fixture.projectId(), toDelete, principal);
        assertThat(countPair(fixture.projectId(), fixture.owner().getId())).isEqualTo(4);
        conversations.create(fixture.projectId(), principal, "Replacement");
        assertThat(countPair(fixture.projectId(), fixture.owner().getId())).isEqualTo(5);

        Fixture anotherProject = fixture();
        membership(anotherProject.projectId(), fixture.owner(), "MEMBER");
        conversations.create(anotherProject.projectId(), principal, "Independent project");
        User anotherUser = user(SystemRole.USER);
        membership(fixture.projectId(), anotherUser, "MEMBER");
        conversations.create(fixture.projectId(), principal(anotherUser), "Independent user");
        assertThat(countPair(anotherProject.projectId(), fixture.owner().getId())).isEqualTo(1);
        assertThat(countPair(fixture.projectId(), anotherUser.getId())).isEqualTo(1);
    }

    @Test
    void groundedSourcesBecomeUnavailableAfterDocumentDeletion() throws Exception {
        Fixture fixture = fixture();
        Evidence evidence = evidence(fixture, "rollout");
        AiTurnResponse answer = conversations.send(fixture.projectId(), evidence.conversationId(),
                principal(fixture.owner()), "rollout");
        assertThat(answer.message().answerType().name()).isEqualTo("GROUNDED");
        assertThat(answer.sources()).singleElement().satisfies(source -> {
            assertThat(source.order()).isEqualTo(1);
            assertThat(source.documentId()).isEqualTo(evidence.documentId());
            assertThat(source.availability().name()).isEqualTo("AVAILABLE");
        });
        assertThat(count("ai_message_sources", "assistant_message_id", answer.message().id()))
                .isEqualTo(1);

        jdbc.update("DELETE FROM documents WHERE id = ?", evidence.documentId());
        var history = conversations.listMessages(fixture.projectId(), evidence.conversationId(),
                principal(fixture.owner()), org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(history.content()).hasSize(2);
        assertThat(history.content().get(1).sources()).singleElement().satisfies(source -> {
            assertThat(source.documentId()).isNull();
            assertThat(source.availability().name()).isEqualTo("UNAVAILABLE");
            assertThat(source.documentName()).isEqualTo("M7 source");
        });
    }

    @Test
    void providerFailureRetainsUserAndMarksAssistantFailed() {
        Fixture fixture = fixture();
        Evidence evidence = evidence(fixture, "rollout");
        fakeChat.failWith(new AiProviderException(AiProviderErrorCategory.UNAVAILABLE));
        assertThatThrownBy(() -> conversations.send(fixture.projectId(), evidence.conversationId(),
                principal(fixture.owner()), "rollout"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_messages WHERE conversation_id = ? "
                + "AND role = 'USER'", Long.class, evidence.conversationId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT generation_status FROM ai_messages WHERE conversation_id = ? "
                + "AND role = 'ASSISTANT'", String.class, evidence.conversationId()))
                .isEqualTo(AiGenerationStatus.FAILED.name());
    }

    @Test
    void providerFailureOnCreateLeavesConversationDiscoverable() throws Exception {
        Fixture fixture = fixture();
        evidenceWithoutConversation(fixture, "first failure");
        fakeChat.failWith(new AiProviderException(AiProviderErrorCategory.TIMEOUT));
        mvc.perform(post(conversationsPath(fixture.projectId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"first failure\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_UNAVAILABLE"));
        mvc.perform(get(conversationsPath(fixture.projectId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        UUID conversationId = jdbc.queryForObject("SELECT id FROM ai_conversations "
                + "WHERE project_id = ?", UUID.class, fixture.projectId());
        mvc.perform(get(conversationsPath(fixture.projectId()) + "/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[1].message.generationStatus").value("FAILED"))
                .andExpect(jsonPath("$.content[1].message.content").isEmpty());
    }

    @Test
    void concurrentSendRejectsLoserWithoutPersistingItsUserMessage() throws Exception {
        Fixture fixture = fixture();
        Evidence evidence = evidence(fixture, "rollout");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean providerSawCommittedMarker = new AtomicBoolean(false);
        doAnswer(invocation -> {
            providerSawCommittedMarker.set(!TransactionSynchronizationManager.isActualTransactionActive()
                    && jdbc.queryForObject("SELECT count(*) FROM ai_messages WHERE conversation_id = ? "
                            + "AND role = 'USER'", Long.class, evidence.conversationId()) == 1
                    && jdbc.queryForObject("SELECT count(*) FROM ai_messages WHERE conversation_id = ? "
                            + "AND role = 'ASSISTANT' AND generation_status = 'PROCESSING'",
                            Long.class, evidence.conversationId()) == 1);
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return fakeChat.generate(invocation.getArgument(0));
        }).when(chatModel).generate(any());
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Object> first = pool.submit(() -> sendOrFailure(fixture, evidence));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> conversations.send(fixture.projectId(), evidence.conversationId(),
                    principal(fixture.owner()), "second"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.AI_REQUEST_IN_PROGRESS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_messages WHERE conversation_id = ? "
                    + "AND role = 'USER'", Long.class, evidence.conversationId())).isEqualTo(1);
            release.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isInstanceOf(AiTurnResponse.class);
        } finally {
            release.countDown();
        }
        assertThat(providerSawCommittedMarker).isTrue();
    }

    @Test
    void membershipRevocationDuringGenerationDeniesCompletionAndMarksFailed() throws Exception {
        Fixture fixture = fixture();
        Evidence evidence = evidence(fixture, "rollout");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return fakeChat.generate(invocation.getArgument(0));
        }).when(chatModel).generate(any());
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Object> pending = pool.submit(() -> sendOrFailure(fixture, evidence));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            jdbc.update("DELETE FROM project_members WHERE project_id = ? AND user_id = ?",
                    fixture.projectId(), fixture.owner().getId());
            release.countDown();
            assertThat(pending.get(20, TimeUnit.SECONDS)).isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ACCESS_FORBIDDEN);
        } finally {
            release.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT generation_status FROM ai_messages "
                + "WHERE conversation_id = ? AND role = 'ASSISTANT'", String.class,
                evidence.conversationId())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT content FROM ai_messages WHERE conversation_id = ? "
                + "AND role = 'ASSISTANT'", String.class, evidence.conversationId())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_message_sources s JOIN ai_messages m "
                + "ON m.id = s.assistant_message_id WHERE m.conversation_id = ?", Long.class,
                evidence.conversationId())).isZero();
    }

    @Test
    void revocationAfterM6ResultButBeforeFinalizationCannotPersistAnswer() throws Exception {
        Fixture fixture = fixture();
        Evidence evidence = evidence(fixture, "rollout");
        CountDownLatch afterRag = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            afterRag.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return null;
        }).when(finalizationHook).beforeFinalization(any(), any());
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Object> pending = pool.submit(() -> sendOrFailure(fixture, evidence));
            assertThat(afterRag.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(fakeChat.invocationCount()).isEqualTo(1);
            jdbc.update("DELETE FROM project_members WHERE project_id = ? AND user_id = ?",
                    fixture.projectId(), fixture.owner().getId());
            release.countDown();
            assertThat(pending.get(20, TimeUnit.SECONDS)).isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ACCESS_FORBIDDEN);
        } finally {
            release.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT generation_status FROM ai_messages "
                + "WHERE conversation_id = ? AND role = 'ASSISTANT'", String.class,
                evidence.conversationId())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_message_sources s JOIN ai_messages m "
                + "ON m.id = s.assistant_message_id WHERE m.conversation_id = ?", Long.class,
                evidence.conversationId())).isZero();
    }

    @Test
    void transientRevokeAndRejoinCannotCompleteTheOldRequest() throws Exception {
        Fixture fixture = fixture();
        Evidence evidence = evidence(fixture, "rollout");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return fakeChat.generate(invocation.getArgument(0));
        }).when(chatModel).generate(any());
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Object> pending = pool.submit(() -> sendOrFailure(fixture, evidence));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            jdbc.update("DELETE FROM project_members WHERE project_id = ? AND user_id = ?",
                    fixture.projectId(), fixture.owner().getId());
            membership(fixture.projectId(), fixture.owner(), "MEMBER");
            release.countDown();

            assertThat(pending.get(20, TimeUnit.SECONDS)).isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ACCESS_FORBIDDEN);
        } finally {
            release.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT generation_status FROM ai_messages "
                + "WHERE conversation_id = ? AND role = 'ASSISTANT' ORDER BY created_at DESC LIMIT 1",
                String.class, evidence.conversationId())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_message_sources s JOIN ai_messages m "
                + "ON m.id = s.assistant_message_id WHERE m.conversation_id = ?", Long.class,
                evidence.conversationId())).isZero();
    }

    @Test
    void deletingConversationDuringGenerationCannotResurrectIt() throws Exception {
        Fixture fixture = fixture();
        Evidence evidence = evidence(fixture, "rollout");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return fakeChat.generate(invocation.getArgument(0));
        }).when(chatModel).generate(any());
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Object> pending = pool.submit(() -> sendOrFailure(fixture, evidence));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            conversations.delete(fixture.projectId(), evidence.conversationId(),
                    principal(fixture.owner()));
            release.countDown();
            assertThat(pending.get(20, TimeUnit.SECONDS)).isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.AI_CONVERSATION_NOT_FOUND);
        } finally {
            release.countDown();
        }
        assertThat(count("ai_conversations", "id", evidence.conversationId())).isZero();
        assertThat(count("ai_messages", "conversation_id", evidence.conversationId())).isZero();
    }

    @Test
    void renameListAndCrossProjectRoutesKeepMetadataPrivate() throws Exception {
        Fixture fixture = fixture();
        Fixture other = fixture();
        var created = conversations.create(fixture.projectId(), principal(fixture.owner()),
                "first question");
        UUID id = created.conversation().id();
        UUID newerId = conversations.create(fixture.projectId(), principal(fixture.owner()),
                "second question").conversation().id();
        String path = conversationsPath(fixture.projectId()) + "/" + id;
        mvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  Updated title  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"));
        mvc.perform(get(conversationsPath(fixture.projectId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Updated title"))
                .andExpect(jsonPath("$.content[1].id").value(newerId.toString()));
        mvc.perform(get(conversationsPath(other.projectId()) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
                .andExpect(status().isForbidden());
        membership(other.projectId(), fixture.owner(), "MEMBER");
        mvc.perform(get(conversationsPath(other.projectId()) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_CONVERSATION_NOT_FOUND"));
        mvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
                .andExpect(status().isNoContent());
        assertThat(count("ai_messages", "conversation_id", id)).isZero();
    }

    @Test
    void distinctConversationsCanGenerateConcurrently() throws Exception {
        Fixture fixture = fixture();
        Evidence first = evidence(fixture, "rollout");
        Evidence second = evidence(fixture, "rollout");
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return fakeChat.generate(invocation.getArgument(0));
        }).when(chatModel).generate(any());
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> a = pool.submit(() -> sendOrFailure(fixture, first));
            Future<Object> b = pool.submit(() -> sendOrFailure(fixture, second));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS)).isInstanceOf(AiTurnResponse.class);
            assertThat(b.get(20, TimeUnit.SECONDS)).isInstanceOf(AiTurnResponse.class);
        } finally {
            release.countDown();
        }
    }

    @Test
    void messagePageAndInputLimitsAreDeterministic() throws Exception {
        Fixture fixture = fixture();
        var created = conversations.create(fixture.projectId(), principal(fixture.owner()), "first");
        UUID id = created.conversation().id();
        conversations.send(fixture.projectId(), id, principal(fixture.owner()), "second");
        var page = conversations.listMessages(fixture.projectId(), id,
                principal(fixture.owner()), org.springframework.data.domain.PageRequest.of(1, 1));
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.content()).singleElement().satisfies(turn ->
                assertThat(turn.message().role().name()).isEqualTo("ASSISTANT"));
        mvc.perform(post(conversationsPath(fixture.projectId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\\u00a0\\u00a0\"}"))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> conversations.send(fixture.projectId(), id,
                principal(fixture.owner()), "x".repeat(8001)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void adminAndUploaderCanRetryFailedIndexWithoutCallingProvider() throws Exception {
        Fixture fixture = fixture();
        User uploader = user(SystemRole.USER);
        User admin = user(SystemRole.ADMIN);
        membership(fixture.projectId(), uploader, "MEMBER");
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (id, project_id, uploaded_by_user_id, display_name, "
                + "original_filename, file_kind, extension, mime_type, size_bytes, storage_key) "
                + "VALUES (?, ?, ?, 'Uploader source', 'source.txt', 'DOCUMENT', 'txt', "
                + "'text/plain', 10, ?)", documentId, fixture.projectId(), uploader.getId(),
                "projects/" + fixture.projectId() + "/documents/" + documentId + ".txt");
        jdbc.update("INSERT INTO document_ai_indexes (document_id, project_id, status, "
                + "desired_version, chunking_version, embedding_model, embedding_dimensions) "
                + "VALUES (?, ?, 'FAILED', 1, 'chunk-v1', 'gemini-embedding-2', 768)",
                documentId, fixture.projectId());
        String path = "/api/v1/projects/" + fixture.projectId() + "/documents/" + documentId + "/ai-index";
        mvc.perform(post(path + "/retry").header(HttpHeaders.AUTHORIZATION, bearer(uploader)))
                .andExpect(status().isAccepted());
        jdbc.update("UPDATE document_ai_indexes SET status = 'FAILED' WHERE document_id = ?", documentId);
        mvc.perform(post(path + "/retry").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isAccepted());
        assertThat(fakeChat.invocationCount()).isZero();
        assertThat(fakeEmbedding.invocationCount()).isZero();
    }

    @Test
    void documentIndexStatusAndFailedOnlyRetryUseExistingPermissionBoundary() throws Exception {
        Fixture fixture = fixture();
        User otherMember = user(SystemRole.USER);
        membership(fixture.projectId(), otherMember, "MEMBER");
        UUID documentId = document(fixture);
        jdbc.update("INSERT INTO document_ai_indexes (document_id, project_id, status, "
                + "failure_reason, desired_version, chunking_version, embedding_model, "
                + "embedding_dimensions) VALUES (?, ?, 'FAILED', 'PROCESSING_ERROR', 1, "
                + "'chunk-v1', 'gemini-embedding-2', 768)", documentId, fixture.projectId());
        String path = "/api/v1/projects/" + fixture.projectId() + "/documents/" + documentId + "/ai-index";
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(otherMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.retryAllowed").value(true));
        mvc.perform(post(path + "/retry").header(HttpHeaders.AUTHORIZATION, bearer(otherMember)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DOCUMENT_MODIFICATION_FORBIDDEN"));
        mvc.perform(post(path + "/retry").header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post(path + "/retry").header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_INDEX_RETRY_NOT_ALLOWED"));
        assertThat(fakeChat.invocationCount()).isZero();
    }

    private Object concurrentCreate(UUID projectId, CustomUserPrincipal principal,
            CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("create start timeout");
            return conversations.create(projectId, principal, "parallel");
        } catch (RuntimeException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return exception;
        }
    }

    private Object sendOrFailure(Fixture fixture, Evidence evidence) {
        try {
            return conversations.send(fixture.projectId(), evidence.conversationId(),
                    principal(fixture.owner()), "rollout");
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private Fixture fixture() {
        User owner = user(SystemRole.USER);
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO projects (id, name) VALUES (?, ?)", projectId,
                "M7 project " + projectId);
        membership(projectId, owner, "OWNER");
        return new Fixture(projectId, owner);
    }

    private User user(SystemRole role) {
        User user = new User("m7-" + UUID.randomUUID() + "@example.invalid", "hash",
                "M7 user", role, UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private void membership(UUID projectId, User user, String role) {
        jdbc.update("INSERT INTO project_members (id, project_id, user_id, role) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), projectId, user.getId(), role);
    }

    private Evidence evidence(Fixture fixture, String question) {
        UUID documentId = evidenceWithoutConversation(fixture, question);
        UUID conversationId = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_conversations (id, project_id, created_by_user_id, title) "
                + "VALUES (?, ?, ?, 'M7 conversation')", conversationId, fixture.projectId(),
                fixture.owner().getId());
        return new Evidence(conversationId, documentId);
    }

    private UUID evidenceWithoutConversation(Fixture fixture, String question) {
        UUID documentId = document(fixture);
        jdbc.update("INSERT INTO document_ai_indexes (document_id, project_id, status, "
                + "active_version, desired_version, chunking_version, embedding_model, "
                + "embedding_dimensions) VALUES (?, ?, 'READY', 1, 1, 'chunk-v1', "
                + "'gemini-embedding-2', 768)", documentId, fixture.projectId());
        UUID chunkId = UUID.randomUUID();
        float[] axis = new float[768];
        axis[0] = 1f;
        vectors.insertDocumentChunk(new DocumentAiChunkInsert(chunkId, fixture.projectId(),
                documentId, 1, 0, "M7 relevant text", 1, null, "Deployment", 3,
                "m7-chunk-hash", axis));
        fakeEmbedding.registerFixture(question, FakeAiEmbeddingModel.unitVector(0));
        return documentId;
    }

    private UUID document(Fixture fixture) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (id, project_id, uploaded_by_user_id, display_name, "
                + "original_filename, file_kind, extension, mime_type, size_bytes, storage_key) "
                + "VALUES (?, ?, ?, 'M7 source', 'm7.txt', 'DOCUMENT', 'txt', 'text/plain', 10, ?)",
                documentId, fixture.projectId(), fixture.owner().getId(),
                "projects/" + fixture.projectId() + "/documents/" + documentId + ".txt");
        return documentId;
    }

    private long count(String table, String column, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class, id);
    }

    private long countPair(UUID projectId, UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM ai_conversations WHERE project_id = ? "
                + "AND created_by_user_id = ?", Long.class, projectId, userId);
    }

    private String bearer(User user) {
        return "Bearer " + jwt.generateAccessToken(user).token();
    }

    private static CustomUserPrincipal principal(User user) {
        return new CustomUserPrincipal(user.getId(), user.getEmail(), user.getSystemRole(),
                user.getStatus(), true);
    }

    private static String conversationsPath(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/ai/conversations";
    }

    private record Fixture(UUID projectId, User owner) { }
    private record Evidence(UUID conversationId, UUID documentId) { }
}
