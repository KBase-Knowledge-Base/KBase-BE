package com.kbase.user.mapper;

import com.kbase.user.dto.response.UserResponse;
import com.kbase.user.entity.User;

import org.springframework.stereotype.Component;

/** Maps the persistent user to its safe API representation. */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getSystemRole(),
                user.getStatus(),
                user.getEmailVerifiedAt() != null,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
