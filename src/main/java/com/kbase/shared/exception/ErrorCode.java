package com.kbase.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable API error codes shared by all Core v1 backend features.
 *
 * <p>The enum owns the transport status and safe default message so that
 * callers do not have to duplicate error literals or expose implementation
 * details from an exception.</p>
 */
public enum ErrorCode {

    // Request and transport errors
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST,
            "Request body is malformed or contains invalid values."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "One or more request parameters are invalid."),
    INVALID_RANGE(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "The requested byte range is invalid."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The HTTP method is not supported for this resource."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "The request media type is not supported."),

    // Authentication and account state
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "The access token is invalid."),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "The access token has expired."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email verification is required."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "The account is disabled."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "The email address is already verified."),
    INVALID_OTP(HttpStatus.BAD_REQUEST, "The verification code is invalid."),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "The verification code has expired."),
    OTP_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS,
            "The maximum number of verification attempts has been exceeded."),
    OTP_RESEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS,
            "Please wait before requesting another verification code."),
    REFRESH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "A refresh token is required."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "The refresh token is invalid."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "The refresh token has expired."),
    REFRESH_SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "The refresh session is no longer valid."),
    CURRENT_PASSWORD_INVALID(HttpStatus.UNAUTHORIZED, "The current password is invalid."),

    // User and project
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "The email address is already registered."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested user was not found."),
    USER_HAS_DEPENDENCIES(HttpStatus.CONFLICT, "The user still has dependent resources."),
    USER_OWNS_PROJECT(HttpStatus.CONFLICT, "The user still owns a project."),
    INVALID_USER_STATUS(HttpStatus.BAD_REQUEST, "The requested user status is invalid."),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested project was not found."),
    PROJECT_ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have access to this project."),
    PROJECT_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN,
            "You do not have permission to manage this project."),
    PROJECT_OWNER_ALREADY_EXISTS(HttpStatus.CONFLICT, "The project already has an owner."),
    PROJECT_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "The project could not be deleted."),

    // Membership and invitation
    PROJECT_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested project member was not found."),
    PROJECT_MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "The user is already a project member."),
    PROJECT_OWNER_REMOVAL_FORBIDDEN(HttpStatus.CONFLICT, "The project owner cannot be removed."),
    OWNER_CANNOT_LEAVE_PROJECT(HttpStatus.CONFLICT, "The project owner cannot leave the project."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested invitation was not found."),
    INVITATION_ALREADY_PENDING(HttpStatus.CONFLICT, "A pending invitation already exists for this email."),
    INVITATION_NOT_PENDING(HttpStatus.CONFLICT, "The invitation is no longer pending."),
    INVITATION_EXPIRED(HttpStatus.CONFLICT, "The invitation has expired."),
    INVITATION_EMAIL_MISMATCH(HttpStatus.FORBIDDEN,
            "The invitation email does not match the current account."),

    // Project organization
    FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested folder was not found."),
    PARENT_FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested parent folder was not found."),
    FOLDER_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "A folder with this name already exists here."),
    FOLDER_CYCLE_DETECTED(HttpStatus.CONFLICT, "The folder move would create a cycle."),
    FOLDER_NOT_EMPTY(HttpStatus.CONFLICT, "The folder is not empty."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested category was not found."),
    CATEGORY_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT,
            "A category with this name already exists in the project."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "The category is still used by one or more documents."),
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested tag was not found."),
    TAG_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT,
            "A tag with this name already exists in the project."),
    TAG_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN,
            "You do not have permission to manage this tag."),

    // Documents and storage
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested document was not found."),
    DOCUMENT_MODIFICATION_FORBIDDEN(HttpStatus.FORBIDDEN,
            "You do not have permission to modify this document."),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "The uploaded file is empty."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file is too large."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The uploaded file type is not supported."),
    MIME_TYPE_MISMATCH(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The file media type is not valid."),
    INVALID_FILE_METADATA(HttpStatus.BAD_REQUEST, "The file metadata is invalid."),
    PREVIEW_NOT_SUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Preview is not supported for this file type."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "The file could not be uploaded."),
    DOCUMENT_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "The document could not be deleted."),
    STORAGE_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "The storage service is temporarily unavailable."),

    // AI Project Assistant and document indexing
    AI_CONVERSATION_LIMIT_REACHED(HttpStatus.CONFLICT,
            "The project conversation limit has been reached."),
    AI_CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND,
            "The requested conversation was not found."),
    AI_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT,
            "A response is already being generated for this conversation."),
    AI_INDEX_RETRY_NOT_ALLOWED(HttpStatus.CONFLICT,
            "Only a failed document index can be retried."),
    AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "The AI service is temporarily unavailable."),
    AI_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS,
            "The AI usage limit has been reached. Please try again later."),
    AI_USAGE_GUARD_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "The AI usage guard is temporarily unavailable."),

    // External services and final fallback
    OTP_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "The verification service is temporarily unavailable."),
    EMAIL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "The email service is temporarily unavailable."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return name();
    }

    public String code() {
        return name();
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public int getStatus() {
        return httpStatus.value();
    }

    public int status() {
        return httpStatus.value();
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
