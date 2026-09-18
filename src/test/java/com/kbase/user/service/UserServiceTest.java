package com.kbase.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.kbase.auth.service.RefreshSessionService;
import com.kbase.document.repository.DocumentRepository;
import com.kbase.invitation.repository.ProjectInvitationRepository;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.repository.ProjectMemberRepository;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.dto.request.ChangePasswordRequest;
import com.kbase.user.dto.request.UpdateProfileRequest;
import com.kbase.user.dto.response.UserResponse;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.mapper.UserMapper;
import com.kbase.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private UserRepository userRepository;
    private ProjectMemberRepository projectMemberRepository;
    private DocumentRepository documentRepository;
    private ProjectInvitationRepository invitationRepository;
    private PasswordEncoder passwordEncoder;
    private RefreshSessionService refreshSessionService;
    private UserService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        documentRepository = mock(DocumentRepository.class);
        invitationRepository = mock(ProjectInvitationRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        refreshSessionService = mock(RefreshSessionService.class);
        service = new UserService(
                userRepository, projectMemberRepository, documentRepository,
                invitationRepository, new UserMapper(), passwordEncoder, refreshSessionService);

        user = new User(
                "user@example.com", "bcrypt-hash", "Example User",
                SystemRole.USER, UserStatus.ACTIVE);
        user.setId(UUID.randomUUID());
    }

    @Test
    void getCurrentUserAndProfileUpdateNeverExposePasswordHash() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.getCurrentUser(user.getId());
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.emailVerified()).isFalse();
        assertThat(String.valueOf(response)).doesNotContain("bcrypt-hash");

        UserResponse updated = service.updateProfile(
                user.getId(), new UpdateProfileRequest("New Display Name"));
        assertThat(updated.displayName()).isEqualTo("New Display Name");
        assertThat(user.getDisplayName()).isEqualTo("New Display Name");
    }

    @Test
    void changePasswordVerifiesCurrentThenRevokesSessions() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.matches("wrong-current", "bcrypt-hash")).thenReturn(false);
        when(passwordEncoder.matches("old-password", "bcrypt-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");

        assertThatThrownBy(() -> service.changePassword(user.getId(),
                new ChangePasswordRequest("wrong-current", "new-password-123")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CURRENT_PASSWORD_INVALID));
        verify(refreshSessionService, never()).revokeAllForUser(user.getId());

        service.changePassword(user.getId(),
                new ChangePasswordRequest("old-password", "new-password-123"));
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshSessionService).revokeAllForUser(user.getId());
    }

    @Test
    void adminStatusChangeMapsUnknownValueAndRevokesOnDisable() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.updateStatus(user.getId(), "SUSPENDED"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_USER_STATUS));
        verify(refreshSessionService, never()).revokeAllForUser(user.getId());

        service.updateStatus(user.getId(), "disabled");
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(refreshSessionService).revokeAllForUser(user.getId());

        service.updateStatus(user.getId(), "ACTIVE");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        // Previously revoked sessions stay revoked: disable remains the only revoke point.
        verify(refreshSessionService, org.mockito.Mockito.times(1)).revokeAllForUser(user.getId());
    }

    @Test
    void deleteUserRejectsOwnerBeforeOtherDependencies() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(projectMemberRepository.existsByUserIdAndRole(user.getId(), ProjectRole.OWNER))
                .thenReturn(true);

        assertThatThrownBy(() -> service.deleteUser(user.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_OWNS_PROJECT));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteUserRejectsDocumentsMembershipsAndInvitations() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(projectMemberRepository.existsByUserIdAndRole(user.getId(), ProjectRole.OWNER))
                .thenReturn(false);

        when(documentRepository.existsByUploadedById(user.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteUser(user.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_HAS_DEPENDENCIES));

        when(documentRepository.existsByUploadedById(user.getId())).thenReturn(false);
        when(projectMemberRepository.existsByUserId(user.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteUser(user.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_HAS_DEPENDENCIES));

        when(projectMemberRepository.existsByUserId(user.getId())).thenReturn(false);
        when(invitationRepository.existsByInvitedById(user.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteUser(user.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_HAS_DEPENDENCIES));

        verify(userRepository, never()).delete(any(User.class));

        when(invitationRepository.existsByInvitedById(user.getId())).thenReturn(false);
        service.deleteUser(user.getId());
        verify(userRepository).delete(user);
    }

    @Test
    void unknownUserMapsToUserNotFound() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUser(unknown))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        assertThatThrownBy(() -> service.deleteUser(unknown))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
