package com.kbase.security.principal;

import java.util.Optional;
import java.util.UUID;

import com.kbase.user.entity.User;
import com.kbase.user.repository.UserRepository;

import org.springframework.stereotype.Component;

/**
 * Loads the current user by ID for JWT authentication. The database record is
 * the authority for account state and system role on every request.
 */
@Component
public class CustomUserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Returns empty when the user no longer exists. */
    public Optional<CustomUserPrincipal> loadUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(CustomUserDetailsService::toPrincipal);
    }

    static CustomUserPrincipal toPrincipal(User user) {
        return new CustomUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getSystemRole(),
                user.getStatus(),
                user.getEmailVerifiedAt() != null);
    }
}
