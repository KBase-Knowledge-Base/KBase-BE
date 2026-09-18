package com.kbase.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin status change request. The status is kept as raw text so that an
 * unknown value maps to {@code INVALID_USER_STATUS} instead of a generic
 * body-binding error.
 */
public record ChangeUserStatusRequest(

        @NotBlank(message = "Status is required")
        String status) {
}
