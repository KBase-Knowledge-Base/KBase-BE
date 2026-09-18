package com.kbase.document.service;

import com.kbase.document.entity.Document;
import com.kbase.project.enums.ProjectRole;
import com.kbase.project.service.ProjectAuthorizationService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.ForbiddenOperationException;
import com.kbase.shared.exception.ErrorCode;

import org.springframework.stereotype.Service;

/** Central document ownership policy; former members fail the project access check. */
@Service
public class DocumentAuthorizationService {
    private final ProjectAuthorizationService projectAuthorizationService;

    public DocumentAuthorizationService(ProjectAuthorizationService projectAuthorizationService) {
        this.projectAuthorizationService = projectAuthorizationService;
    }

    public void requireReadPermission(Document document, CustomUserPrincipal principal) {
        projectAuthorizationService.requireProjectAccess(document.getProject().getId(), principal);
    }

    public void requireModifyPermission(Document document, CustomUserPrincipal principal) {
        var access = projectAuthorizationService.requireProjectAccess(document.getProject().getId(), principal);
        if (access.adminOverride() || access.hasRole(ProjectRole.OWNER)
                || (access.hasRole(ProjectRole.MEMBER)
                && document.getUploadedBy().getId().equals(principal.getUserId()))) {
            return;
        }
        throw new ForbiddenOperationException(ErrorCode.DOCUMENT_MODIFICATION_FORBIDDEN);
    }
}
