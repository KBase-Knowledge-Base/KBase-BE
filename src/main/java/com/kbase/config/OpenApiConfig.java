package com.kbase.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata and the shared bearerAuth scheme. Security requirements
 * are deliberately per controller/operation — public auth endpoints must not
 * inherit a Bearer requirement — and this configuration never contains
 * business rules.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_BEARER = "bearerAuth";

    public static final String TAG_AUTHENTICATION = "Authentication";
    public static final String TAG_USERS = "Users";
    public static final String TAG_ADMIN_USERS = "Admin - Users";
    public static final String TAG_PROJECTS = "Projects";
    public static final String TAG_ADMIN_PROJECTS = "Admin - Projects";
    public static final String TAG_PROJECT_MEMBERS = "Project Members";
    public static final String TAG_PROJECT_INVITATIONS = "Project Invitations";
    public static final String TAG_INVITATIONS = "Invitations";
    public static final String TAG_FOLDERS = "Folders";
    public static final String TAG_CATEGORIES = "Categories";
    public static final String TAG_TAGS = "Tags";
    public static final String TAG_DOCUMENTS = "Documents";
    public static final String TAG_AI_PROJECT_ASSISTANT = "AI - Project Assistant";
    public static final String TAG_AI_DOCUMENT_INDEXING = "AI - Document Indexing";

    @Bean
    public OpenAPI kbaseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("KBase API")
                        .version("v1")
                        .description("""
                                REST API for KBase Core v1 knowledge-base management: authentication, \
                                projects, membership, invitations, folders, categories, tags and documents. \
                                Registration email verification uses a 6-digit OTP stored as short-lived Redis state \
                                and delivered through Gmail SMTP; the OTP is for email verification only. \
                                Project invitations use a separate secure invitation token sent by email. \
                                Protected endpoints take a JWT access token through the bearerAuth scheme; the \
                                refresh token travels only in an HttpOnly cookie. Document upload uses multipart \
                                form data; preview and download stream the private binary through the backend. \
                                Core document search is metadata-only. AI v1 adds private, non-streaming Project \
                                Assistant conversations and document indexing status/retry, with current project \
                                authorization and strict grounded or NO_EVIDENCE answers."""))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_BEARER,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .tags(openApiTags());
    }

    private static List<Tag> openApiTags() {
        return List.of(
                new Tag().name(TAG_AUTHENTICATION)
                        .description("Register, email verification, login, refresh and logout. Public endpoints; the refresh token lives in an HttpOnly cookie."),
                new Tag().name(TAG_USERS)
                        .description("Current-user profile and password APIs; authenticated user required."),
                new Tag().name(TAG_ADMIN_USERS)
                        .description("System-wide user management. Requires SystemRole.ADMIN."),
                new Tag().name(TAG_PROJECTS)
                        .description("Project management for the current user; project access is MEMBER/OWNER or system ADMIN."),
                new Tag().name(TAG_ADMIN_PROJECTS)
                        .description("System-wide project listing. Requires SystemRole.ADMIN."),
                new Tag().name(TAG_PROJECT_MEMBERS)
                        .description("Project membership listing, removal and leaving; documents are never deleted by membership operations."),
                new Tag().name(TAG_PROJECT_INVITATIONS)
                        .description("Project invitation lifecycle (create/list/resend/cancel). OWNER or system ADMIN only."),
                new Tag().name(TAG_INVITATIONS)
                        .description("Authenticated invitation acceptance with the raw token from the invitation email link."),
                new Tag().name(TAG_FOLDERS)
                        .description("Project-scoped folder hierarchy. Read: any project member; manage: OWNER or system ADMIN."),
                new Tag().name(TAG_CATEGORIES)
                        .description("Project-scoped categories. Read: any project member; manage: OWNER or system ADMIN."),
                new Tag().name(TAG_TAGS)
                        .description("Project-scoped tags. Create/read: any project member; rename/delete: OWNER or system ADMIN."),
                new Tag().name(TAG_DOCUMENTS)
                        .description("Document upload, metadata, search, download, preview and hard delete with project-scoped authorization."),
                new Tag().name(TAG_AI_PROJECT_ASSISTANT)
                        .description("Private creator-owned project conversations and grounded assistant turns."),
                new Tag().name(TAG_AI_DOCUMENT_INDEXING)
                        .description("Document AI index status and asynchronous failed-index retry."));
    }

    /**
     * springdoc merges controller-scanned tags with the tags declared here,
     * which duplicates entries and randomizes order. Rebuilding the tag list
     * from this canonical set keeps exactly one ordered entry per feature tag;
     * a new tag must be registered here, which the contract test enforces. A
     * controller-level tag description, when present, wins over the canonical
     * one.
     */
    @Bean
    public OpenApiCustomizer tagCanonicalizer() {
        return openApi -> {
            Map<String, Tag> canonicalTags = new LinkedHashMap<>();
            openApiTags().forEach(tag -> canonicalTags.put(tag.getName(), tag));
            List<Tag> scannedTags = openApi.getTags() == null ? List.of() : openApi.getTags();
            for (Tag scanned : scannedTags) {
                Tag canonical = canonicalTags.get(scanned.getName());
                if (canonical != null && scanned.getDescription() != null && !scanned.getDescription().isBlank()) {
                    canonical.setDescription(scanned.getDescription());
                }
            }
            openApi.setTags(new ArrayList<>(canonicalTags.values()));
        };
    }

    /**
     * Protected operations document the common 401 contract once, through a
     * shared response schema instead of repeating it on every method.
     */
    @Bean
    public OperationCustomizer authenticationErrorCustomizer() {
        return (operation, handlerMethod) -> {
            boolean bearerRequired = operation.getSecurity() != null && operation.getSecurity().stream()
                    .anyMatch(requirement -> requirement.containsKey(SECURITY_SCHEME_BEARER));
            if (bearerRequired) {
                ApiResponses responses = operation.getResponses() == null
                        ? new ApiResponses()
                        : operation.getResponses();
                responses.computeIfAbsent("401", code -> new ApiResponse()
                        .description("AUTHENTICATION_REQUIRED — missing, invalid or expired access token")
                        .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                                new MediaType().schema(new Schema<>().$ref(
                                        "#/components/schemas/" + com.kbase.shared.response.ApiErrorResponse.class.getSimpleName())))));
                operation.setResponses(responses);
            }
            return operation;
        };
    }
}
