-- KBase Core v1 - partial and expression unique indexes (DB-03)
-- Source of truth: docs/design-docs/KBase - Core v1 Physical Database Design.md
-- (sections 8, 10, 13, 15, 16). PostgreSQL-specific: H2 cannot represent these semantics.

-- Section 8: at most one OWNER per project (at-least-one OWNER is enforced by the
-- application transaction, not by SQL)
CREATE UNIQUE INDEX uq_project_members_single_owner
    ON project_members(project_id)
    WHERE role = 'OWNER';

-- Section 10: one PENDING invitation per (project, email)
CREATE UNIQUE INDEX uq_project_pending_invitation_email
    ON project_invitations(project_id, email)
    WHERE status = 'PENDING';

-- Section 13: case-insensitive root folder name per project
CREATE UNIQUE INDEX uq_folders_root_name
    ON folders(project_id, LOWER(name))
    WHERE parent_id IS NULL;

-- Section 13: case-insensitive child folder name per sibling group
CREATE UNIQUE INDEX uq_folders_child_name
    ON folders(project_id, parent_id, LOWER(name))
    WHERE parent_id IS NOT NULL;

-- Section 15: case-insensitive category name per project
CREATE UNIQUE INDEX uq_categories_project_name
    ON categories(project_id, LOWER(name));

-- Section 16: case-insensitive tag name per project
CREATE UNIQUE INDEX uq_tags_project_name
    ON tags(project_id, LOWER(name));
