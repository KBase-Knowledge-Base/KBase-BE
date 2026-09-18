-- KBase Core v1 - query indexes (DB-04)
-- Source of truth: docs/design-docs/KBase - Core v1 Physical Database Design.md
-- (sections 5, 7, 10, 21, 23). Covers membership, invitation, documents,
-- folder/category, uploader, document_tags and refresh session lookups.

CREATE INDEX idx_refresh_sessions_user_id
    ON refresh_sessions(user_id);

CREATE INDEX idx_refresh_sessions_expires_at
    ON refresh_sessions(expires_at);

CREATE INDEX idx_project_members_user_id
    ON project_members(user_id);

CREATE INDEX idx_project_members_project_id
    ON project_members(project_id);

CREATE INDEX idx_project_invitations_project_status
    ON project_invitations(project_id, status);

CREATE INDEX idx_project_invitations_expires_at
    ON project_invitations(expires_at);

CREATE INDEX idx_documents_project_created_at
    ON documents(project_id, created_at DESC);

CREATE INDEX idx_documents_project_folder
    ON documents(project_id, folder_id);

CREATE INDEX idx_documents_project_category
    ON documents(project_id, category_id);

CREATE INDEX idx_documents_project_file_kind
    ON documents(project_id, file_kind);

CREATE INDEX idx_documents_uploaded_by
    ON documents(uploaded_by_user_id);

CREATE INDEX idx_document_tags_tag_id
    ON document_tags(tag_id);

CREATE INDEX idx_document_tags_project_id
    ON document_tags(project_id);
