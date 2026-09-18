package com.kbase.security.principal;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Runtime authentication principal built from the current database User.
 * Carries only system-level identity; project roles are never part of it.
 */
public final class CustomUserPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final SystemRole systemRole;
    private final UserStatus status;
    private final boolean emailVerified;

    public CustomUserPrincipal(
            UUID userId,
            String email,
            SystemRole systemRole,
            UserStatus status,
            boolean emailVerified) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.email = Objects.requireNonNull(email, "email");
        this.systemRole = Objects.requireNonNull(systemRole, "systemRole");
        this.status = Objects.requireNonNull(status, "status");
        this.emailVerified = emailVerified;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID id() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public SystemRole getSystemRole() {
        return systemRole;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.name()));
    }

    /** Password material is intentionally never held in the runtime principal. */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return isActive();
    }

    @Override
    public boolean isAccountNonLocked() {
        return isActive();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive();
    }

    @Override
    public String toString() {
        return "CustomUserPrincipal{userId=" + userId + ", systemRole=" + systemRole
                + ", status=" + status + ", emailVerified=" + emailVerified + "}";
    }
}
