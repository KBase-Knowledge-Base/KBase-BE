package com.kbase.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintViolationTranslatorTest {

    private final ConstraintViolationTranslator translator = new ConstraintViolationTranslator();

    @Test
    void allDesignedConstraintNamesHaveStableMappings() {
        assertThat(translator.translate(exception("uq_users_email")))
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        assertThat(translator.translate(exception("uq_project_members_project_user")))
                .isEqualTo(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        assertThat(translator.translate(exception("uq_project_members_single_owner")))
                .isEqualTo(ErrorCode.PROJECT_OWNER_ALREADY_EXISTS);
        assertThat(translator.translate(exception("uq_project_pending_invitation_email")))
                .isEqualTo(ErrorCode.INVITATION_ALREADY_PENDING);
        assertThat(translator.translate(exception("uq_folders_root_name")))
                .isEqualTo(ErrorCode.FOLDER_NAME_ALREADY_EXISTS);
        assertThat(translator.translate(exception("uq_folders_child_name")))
                .isEqualTo(ErrorCode.FOLDER_NAME_ALREADY_EXISTS);
        assertThat(translator.translate(exception("uq_categories_project_name")))
                .isEqualTo(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        assertThat(translator.translate(exception("uq_tags_project_name")))
                .isEqualTo(ErrorCode.TAG_NAME_ALREADY_EXISTS);
    }

    @Test
    void unknownConstraintDoesNotGuessBusinessMeaning() {
        DataIntegrityViolationException exception = exception("fk_documents_folder_same_project");

        assertThat(translator.extractConstraintName(exception))
                .contains("fk_documents_folder_same_project");
        assertThat(translator.translate(exception)).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    void nonDatabaseThrowableFallsBackToInternalError() {
        assertThat(translator.translate(new IllegalStateException("uq_users_email")))
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private DataIntegrityViolationException exception(String constraint) {
        return new DataIntegrityViolationException(
                "could not execute statement; ERROR: duplicate key value violates unique constraint \""
                        + constraint + "\"");
    }
}
