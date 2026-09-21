package com.kbase.invitation.service;

import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;

/**
 * Thrown when an invitation is accepted after its expiry. The accept
 * transaction commits the {@code EXPIRED} status write for this error only
 * (see {@code InvitationService.accept} noRollbackFor), so the persisted
 * lifecycle matches the status model while the caller still receives the
 * {@code INVITATION_EXPIRED} error contract.
 */
public class InvitationExpiredException extends BusinessException {

    public InvitationExpiredException() {
        super(ErrorCode.INVITATION_EXPIRED);
    }
}
