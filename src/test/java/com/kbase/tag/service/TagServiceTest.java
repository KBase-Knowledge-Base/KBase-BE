package com.kbase.tag.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.kbase.project.entity.Project;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.tag.dto.request.CreateTagRequest;
import com.kbase.tag.dto.request.UpdateTagRequest;
import com.kbase.tag.entity.Tag;
import com.kbase.tag.mapper.TagMapper;
import com.kbase.tag.repository.TagRepository;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private ProjectAuthorizationService authorizationService;
    @Mock
    private TagMapper tagMapper;

    private TagService service;
    private Project project;
    private CustomUserPrincipal member;
    private CustomUserPrincipal owner;

    @BeforeEach
    void setUp() {
        service = new TagService(tagRepository, authorizationService, tagMapper);
        project = new Project("Project", null);
        project.setId(UUID.randomUUID());
        member = principal();
        owner = principal();
        lenient().when(authorizationService.requireProjectAccess(eq(project.getId()), eq(member)))
                .thenReturn(new ProjectAuthorizationService.ProjectAccess(
                        project, ProjectRole.MEMBER, false));
        lenient().when(authorizationService.requireProjectAccess(eq(project.getId()), eq(owner)))
                .thenReturn(new ProjectAuthorizationService.ProjectAccess(
                        project, ProjectRole.OWNER, false));
    }

    @Test
    void memberCanCreateButCannotRenameOrDeleteSharedTag() {
        when(tagRepository.existsByProjectIdAndNameIgnoreCase(project.getId(), "urgent"))
                .thenReturn(false);
        Tag tag = new Tag(project, "urgent");
        tag.setId(UUID.randomUUID());
        when(tagRepository.save(any(Tag.class))).thenReturn(tag);
        when(tagMapper.toResponse(tag)).thenReturn(null);
        service.createTag(project.getId(), new CreateTagRequest(" urgent "), member);

        assertThatThrownBy(() -> service.renameTag(project.getId(), tag.getId(),
                new UpdateTagRequest("important"), member))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.TAG_MANAGEMENT_FORBIDDEN);
        assertThatThrownBy(() -> service.deleteTag(project.getId(), tag.getId(), member))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.TAG_MANAGEMENT_FORBIDDEN);
        verify(tagRepository, never()).delete(any());
    }

    @Test
    void ownerCanRenameAndDeleteTagAndDuplicateIsCaseInsensitive() {
        when(tagRepository.existsByProjectIdAndNameIgnoreCase(project.getId(), "JWT"))
                .thenReturn(true);
        assertThatThrownBy(() -> service.createTag(
                project.getId(), new CreateTagRequest("JWT"), member))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.TAG_NAME_ALREADY_EXISTS);

        Tag tag = new Tag(project, "jwt");
        tag.setId(UUID.randomUUID());
        when(tagRepository.findByIdAndProjectId(tag.getId(), project.getId()))
                .thenReturn(Optional.of(tag));
        when(tagRepository.existsByProjectIdAndNameIgnoreCaseAndIdNot(
                project.getId(), "security", tag.getId())).thenReturn(false);
        when(tagMapper.toResponse(tag)).thenReturn(null);
        service.renameTag(project.getId(), tag.getId(), new UpdateTagRequest("security"), owner);
        service.deleteTag(project.getId(), tag.getId(), owner);
        verify(tagRepository).delete(tag);
    }

    private static CustomUserPrincipal principal() {
        return new CustomUserPrincipal(UUID.randomUUID(), UUID.randomUUID() + "@example.com",
                SystemRole.USER, UserStatus.ACTIVE, true);
    }
}
