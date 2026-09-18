package com.kbase.user.service;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.kbase.auth.service.RefreshSessionService;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.invitation.repository.ProjectInvitationRepository;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.pagination.PageResponse;
import com.kbase.user.dto.request.ChangePasswordRequest;
import com.kbase.user.dto.request.UpdateProfileRequest;
import com.kbase.user.dto.response.UserResponse;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.mapper.UserMapper;
import com.kbase.user.repository.UserRepository;
import com.kbase.user.repository.UserSpecification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Current-user profile/password management and admin user management.
 * Hard delete follows the dependency rules: owned projects block first,
 * then uploaded documents, memberships and invitation references. Project
 * knowledge is never cascade-deleted through a user.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final DocumentRepository documentRepository;
    private final ProjectInvitationRepository invitationRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshSessionService refreshSessionService;

    public UserService(
            UserRepository userRepository,
            ProjectMemberRepository projectMemberRepository,
            DocumentRepository documentRepository,
            ProjectInvitationRepository invitationRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            RefreshSessionService refreshSessionService) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.projectMemberRepository =
                Objects.requireNonNull(projectMemberRepository, "projectMemberRepository");
        this.documentRepository = Objects.requireNonNull(documentRepository, "documentRepository");
        this.invitationRepository = Objects.requireNonNull(invitationRepository, "invitationRepository");
        this.userMapper = Objects.requireNonNull(userMapper, "userMapper");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.refreshSessionService =
                Objects.requireNonNull(refreshSessionService, "refreshSessionService");
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID currentUserId) {
        return userMapper.toResponse(requireUser(currentUserId));
    }

    @Transactional
    public UserResponse updateProfile(UUID currentUserId, UpdateProfileRequest request) {
        Objects.requireNonNull(request, "request");
        User user = requireUser(currentUserId);
        user.setDisplayName(request.displayName().trim());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(UUID currentUserId, ChangePasswordRequest request) {
        Objects.requireNonNull(request, "request");
        User user = requireUser(currentUserId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // Security baseline: existing sessions must re-authenticate.
        refreshSessionService.revokeAllForUser(currentUserId);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(
            String q, UserStatus status, SystemRole systemRole, Pageable pageable) {
        Page<UserResponse> page = userRepository
                .findAll(UserSpecification.withFilters(q, status, systemRole), pageable)
                .map(userMapper::toResponse);
        return PageResponse.from(page, response -> response);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID targetUserId) {
        return userMapper.toResponse(requireUser(targetUserId));
    }

    @Transactional
    public UserResponse updateStatus(UUID targetUserId, String rawStatus) {
        UserStatus status = parseStatus(rawStatus);
        User user = requireUser(targetUserId);
        user.setStatus(status);
        User saved = userRepository.save(user);
        if (status == UserStatus.DISABLED) {
            refreshSessionService.revokeAllForUser(targetUserId);
        }
        return userMapper.toResponse(saved);
    }

    @Transactional
    public void deleteUser(UUID targetUserId) {
        requireUser(targetUserId);
        if (projectMemberRepository.existsByUserIdAndRole(targetUserId, ProjectRole.OWNER)) {
            throw new BusinessException(ErrorCode.USER_OWNS_PROJECT);
        }
        if (documentRepository.existsByUploadedById(targetUserId)
                || projectMemberRepository.existsByUserId(targetUserId)
                || invitationRepository.existsByInvitedById(targetUserId)) {
            throw new BusinessException(ErrorCode.USER_HAS_DEPENDENCIES);
        }
        // refresh_sessions rows cascade by schema; project knowledge is never
        // deleted through the user row.
        userRepository.delete(requireUser(targetUserId));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private static UserStatus parseStatus(String rawStatus) {
        if (rawStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_USER_STATUS);
        }
        try {
            return UserStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_USER_STATUS);
        }
    }
}
