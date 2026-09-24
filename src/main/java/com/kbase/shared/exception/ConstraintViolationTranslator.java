package com.kbase.shared.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Translates only explicitly designed PostgreSQL constraint names.
 * Unknown constraints intentionally resolve to the generic internal error.
 */
@Component
public class ConstraintViolationTranslator {

    private static final Pattern QUOTED_CONSTRAINT = Pattern.compile(
            "\\bconstraint\\s+\\\"([A-Za-z0-9_]+)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNQUOTED_CONSTRAINT = Pattern.compile(
            "\\bconstraint\\s+([A-Za-z0-9_]+)", Pattern.CASE_INSENSITIVE);

    private static final Map<String, ErrorCode> CONSTRAINT_TO_ERROR_CODE;

    static {
        Map<String, ErrorCode> mappings = new LinkedHashMap<>();
        mappings.put("uq_users_email", ErrorCode.EMAIL_ALREADY_EXISTS);
        mappings.put("uq_project_members_project_user", ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        mappings.put("uq_project_members_single_owner", ErrorCode.PROJECT_OWNER_ALREADY_EXISTS);
        mappings.put("uq_project_pending_invitation_email", ErrorCode.INVITATION_ALREADY_PENDING);
        mappings.put("uq_folders_root_name", ErrorCode.FOLDER_NAME_ALREADY_EXISTS);
        mappings.put("uq_folders_child_name", ErrorCode.FOLDER_NAME_ALREADY_EXISTS);
        mappings.put("uq_categories_project_name", ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        mappings.put("uq_tags_project_name", ErrorCode.TAG_NAME_ALREADY_EXISTS);
        mappings.put("uq_ai_messages_active_generation", ErrorCode.AI_REQUEST_IN_PROGRESS);
        CONSTRAINT_TO_ERROR_CODE = Collections.unmodifiableMap(mappings);
    }

    /** Returns the mapped API code, or INTERNAL_SERVER_ERROR for unknown constraints. */
    public ErrorCode translate(DataIntegrityViolationException exception) {
        return extractConstraintName(exception)
                .map(CONSTRAINT_TO_ERROR_CODE::get)
                .filter(java.util.Objects::nonNull)
                .orElse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /** Convenience overload for callers that only have a persistence throwable. */
    public ErrorCode translate(Throwable exception) {
        if (exception instanceof DataIntegrityViolationException dataIntegrityViolation) {
            return translate(dataIntegrityViolation);
        }
        return ErrorCode.INTERNAL_SERVER_ERROR;
    }

    /** Extracts a database constraint identifier without assigning business meaning to it. */
    public Optional<String> extractConstraintName(DataIntegrityViolationException exception) {
        if (exception == null) {
            return Optional.empty();
        }

        Throwable current = exception;
        java.util.Set<Throwable> visited = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        while (current != null && visited.add(current)) {
            Optional<String> fromMessage = extractFromMessage(current.getMessage());
            if (fromMessage.isPresent()) {
                return fromMessage;
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    public Map<String, ErrorCode> knownMappings() {
        return CONSTRAINT_TO_ERROR_CODE;
    }

    private Optional<String> extractFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = QUOTED_CONSTRAINT.matcher(message);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            return Optional.of(candidate);
        }

        Matcher unquotedMatcher = UNQUOTED_CONSTRAINT.matcher(message);
        while (unquotedMatcher.find()) {
            return Optional.of(unquotedMatcher.group(1));
        }

        // Some JDBC/Hibernate wrappers retain only the identifier without the
        // PostgreSQL sentence. Match a whole identifier, never a substring.
        for (String candidate : CONSTRAINT_TO_ERROR_CODE.keySet()) {
            Pattern token = Pattern.compile("(?<![A-Za-z0-9_])"
                    + Pattern.quote(candidate) + "(?![A-Za-z0-9_])");
            if (token.matcher(message).find()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
