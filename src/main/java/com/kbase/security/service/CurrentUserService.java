package com.kbase.security.service;

import java.util.UUID;

import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.enums.SystemRole;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Small adapter over the Spring Security context. It must never grow project
 * or document permission queries.
 */
@Component
public class CurrentUserService {

    public CustomUserPrincipal requirePrincipal() {
        CustomUserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return principal;
    }

    public UUID requireUserId() {
        return requirePrincipal().getUserId();
    }

    public boolean isAdmin() {
        CustomUserPrincipal principal = currentPrincipal();
        return principal != null
                && principal.getSystemRole() == SystemRole.ADMIN;
    }

    private static CustomUserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof CustomUserPrincipal principal
                ? principal
                : null;
    }
}
