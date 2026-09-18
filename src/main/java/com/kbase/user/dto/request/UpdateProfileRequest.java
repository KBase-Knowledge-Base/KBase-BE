package com.kbase.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Profile update request. Only displayName is user-modifiable. */
public record UpdateProfileRequest(

        @NotBlank(message = "Display name is required")
        @Size(max = 100, message = "Display name must contain at most 100 characters")
        String displayName) {
}
