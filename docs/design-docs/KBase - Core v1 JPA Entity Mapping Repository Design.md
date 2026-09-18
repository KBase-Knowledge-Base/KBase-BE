# KBase – Core v1
## JPA Entity Mapping + Repository Design

**Version:** Draft 3  
**Backend:** Java Spring Boot  
**Persistence:** Spring Data JPA / Hibernate  
**Database:** PostgreSQL  
**Schema Management:** Flyway baseline recommendation  
**Architecture:** Feature-first Modular Monolith  
**AI/RAG:** Out of scope for Core v1

---

# 1. Purpose

Tài liệu này chuyển **Physical Database Design** của KBase Core v1 sang thiết kế JPA/Hibernate và Spring Data Repository.

Tài liệu kế thừa trực tiếp từ:

- Core v1 Specification
- Entity Analysis & ERD
- Physical Database Design
- REST API Specification
- Spring Boot Application Architecture + Module/Package Structure

Mục tiêu:

- Mapping chính xác 10 bảng PostgreSQL sang JPA Entity.
- Giữ nguyên các FK và cross-project integrity đã thiết kế.
- Xác định ownership của từng entity trong package.
- Chốt relationship mapping và fetch strategy.
- Tránh cascade nguy hiểm.
- Thiết kế Repository API theo use case thay vì expose persistence tùy tiện.
- Chuẩn bị cho Service Layer và Spring Security implementation.

---

# 2. Entities in Core v1

Core v1 hiện có đúng 10 persistent entities/tables:

```text
users
refresh_sessions

projects
project_members
project_invitations

folders
categories
tags

documents
document_tags
```

Java entities:

```text
User
RefreshSession

Project
ProjectMember
ProjectInvitation

Folder
Category
Tag

Document
DocumentTag
```

Không có entity AI trong Core v1.

---

# 3. Package Ownership

```text
com.kbase
│
├── auth
│   ├── entity/RefreshSession.java
│   └── repository/RefreshSessionRepository.java
│
├── user
│   ├── entity/User.java
│   └── repository/UserRepository.java
│
├── project
│   ├── entity/Project.java
│   ├── entity/ProjectMember.java
│   ├── repository/ProjectRepository.java
│   └── repository/ProjectMemberRepository.java
│
├── invitation
│   ├── entity/ProjectInvitation.java
│   └── repository/ProjectInvitationRepository.java
│
├── folder
│   ├── entity/Folder.java
│   └── repository/FolderRepository.java
│
├── category
│   ├── entity/Category.java
│   └── repository/CategoryRepository.java
│
├── tag
│   ├── entity/Tag.java
│   └── repository/TagRepository.java
│
└── document
    ├── entity/Document.java
    ├── entity/DocumentTag.java
    ├── entity/DocumentTagId.java
    ├── repository/DocumentRepository.java
    └── repository/DocumentTagRepository.java
```

---

# 4. General JPA Mapping Rules

Core v1 follows these rules:

1. Use explicit `@Table` and `@Column` names matching PostgreSQL schema.
2. Never expose Entity directly through REST.
3. Use `EnumType.STRING`.
4. Prefer `FetchType.LAZY` for entity relationships.
5. Do not use `CascadeType.ALL` by default.
6. Avoid large bidirectional entity graphs.
7. Prefer child → parent `@ManyToOne` mappings.
8. Flyway remains the authoritative schema definition.
9. JPA annotations do not replace partial indexes, expression indexes, composite constraints, or check constraints.
10. Service layer owns transaction boundaries.

### 4.1 Composite-column association implementation note (Hibernate 7)

Hibernate 7 rejects a composite association when one `@JoinColumn` is
writable and another join column is read-only while both refer to the same
physical column. The verified implementation therefore keeps the composite
foreign-key values as scalar mappings and exposes the entity relationships as
read-only views:

- `Folder.parentId` is the writable `parent_id`; `Folder.parent` is a lazy,
  read-only view joined by `(parent_id, project_id)`.
- `Document.folderId` and `Document.categoryId` are the writable nullable
  foreign-key values; `Document.folder` and `Document.category` are lazy,
  read-only views joined by the corresponding `(id, project_id)` key.
- `DocumentTag.id.documentId`, `DocumentTag.id.tagId` and
  `DocumentTag.projectId` are the writable junction-row values;
  `DocumentTag.document` and `DocumentTag.tag` are lazy, read-only views.
- On entities that participate in these joins, `project` remains the writable
  `project_id` association and `projectId` is a read-only scalar view of the
  same column.

This is an ORM mapping refinement only. It does not remove or weaken any
composite foreign key in Flyway, and `project_id` remains present wherever
the physical schema requires it. Entity setters and lifecycle callbacks keep
the scalar foreign-key view synchronized when an association is assigned.

---

# 5. Why Flyway Remains Source of Truth

Một số constraint quan trọng không thể mô tả đầy đủ bằng annotation JPA chuẩn.

Ví dụ:

```sql
CREATE UNIQUE INDEX uq_project_members_single_owner
    ON project_members(project_id)
    WHERE role = 'OWNER';
```

```sql
CREATE UNIQUE INDEX uq_categories_project_name
    ON categories(project_id, LOWER(name));
```

```sql
CREATE UNIQUE INDEX uq_folders_root_name
    ON folders(project_id, LOWER(name))
    WHERE parent_id IS NULL;
```

Do đó:

```text
Flyway SQL = authoritative physical schema
JPA annotations = object-relational mapping metadata
```

Recommended:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Không dùng `ddl-auto=update` làm production schema management.

---

# 6. UUID Mapping Strategy

Physical schema dùng:

```sql
id UUID PRIMARY KEY
```

Application-side generation là baseline hiện tại.

Nếu version JPA/Hibernate được chọn hỗ trợ tốt:

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
@Column(name = "id", nullable = false, updatable = false)
private UUID id;
```

Hoặc Hibernate 6:

```java
@Id
@UuidGenerator
@Column(name = "id", nullable = false, updatable = false)
private UUID id;
```

Exact annotation sẽ được chốt khi Spring Boot/Hibernate version được khóa.

---

# 7. Timestamp Mapping

PostgreSQL:

```text
TIMESTAMPTZ
```

Java recommendation:

```java
Instant
```

Ví dụ:

```java
@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;

@Column(name = "updated_at", nullable = false)
private Instant updatedAt;
```

Có thể dùng Spring Data auditing:

```java
@CreatedDate
@LastModifiedDate
```

`Instant` giúp entity không phụ thuộc timezone hiển thị.

---

# 8. Base Entity Decision

Core v1 **không bắt buộc** có generic `BaseEntity`.

Lý do: các bảng không có cùng bộ timestamp.

```text
User           → created_at + updated_at
Tag            → created_at
RefreshSession → created_at
```

Baseline: giữ field explicit trên từng entity.

---

# 9. Enum Mapping

Enums:

```text
SystemRole
UserStatus
ProjectRole
InvitationStatus
FileKind
```

Mapping:

```java
@Enumerated(EnumType.STRING)
@Column(name = "...", nullable = false)
private ... value;
```

Không dùng `EnumType.ORDINAL`.

---

# 10. User Entity

```java
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false, length = 20)
    private SystemRole systemRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

Không cần map các collection như:

```text
User.sessions
User.documents
User.projectMembers
```

nếu chưa có use case cụ thể cần traverse từ User.

`emailVerifiedAt == null` nghĩa là account chưa hoàn thành registration email verification. REST layer có thể expose derived field:

```text
emailVerified = emailVerifiedAt != null
```

OTP verification state itself không phải JPA Entity. `OtpStore` sử dụng Redis và chỉ giữ dữ liệu ngắn hạn có TTL.

---

# 11. RefreshSession Entity

```java
@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

Không cascade từ RefreshSession sang User.

`RefreshSessionRepository` vẫn là PostgreSQL-backed session store. Không chuyển refresh token/session sang Redis chỉ vì Redis được thêm cho OTP.

---

# 12. Project Entity

```java
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

Quan trọng:

```text
NO owner_id
```

OWNER chỉ được xác định qua:

```text
ProjectMember.role = OWNER
```

---

# 13. ProjectMember Entity

```java
@Entity
@Table(
    name = "project_members",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_project_members_project_user",
            columnNames = {"project_id", "user_id"}
        )
    }
)
public class ProjectMember {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ProjectRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;
}
```

Rule “mỗi project tối đa một OWNER” vẫn do PostgreSQL partial unique index enforce.

---

# 14. ProjectInvitation Entity

```java
@Entity
@Table(name = "project_invitations")
public class ProjectInvitation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private User invitedBy;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

Unique pending invitation vẫn do partial index ở PostgreSQL enforce.

---

# 15. Folder Entity

Physical schema cố ý enforce:

```text
parent.project_id = folder.project_id
```

Mapping:

```java
@Entity
@Table(
    name = "folders",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_folders_id_project",
            columnNames = {"id", "project_id"}
        )
    }
)
public class Folder {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "project_id", nullable = false, insertable = false, updatable = false)
    private UUID projectId;

    @Column(name = "parent_id")
    private UUID parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(
            name = "parent_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false
        ),
        @JoinColumn(
            name = "project_id",
            referencedColumnName = "project_id",
            insertable = false,
            updatable = false
        )
    })
    private Folder parent;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

`parentId` là writable source của `parent_id`; `parent` và cả hai cột join
trong association là read-only views. `project_id` vẫn được map bởi
`project`, còn `projectId` chỉ là column view để Hibernate có thể resolve
composite target keys.

Không cần `@OneToMany children` trong Core v1.

---

# 16. Category Entity

```java
@Entity
@Table(
    name = "categories",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_categories_id_project",
            columnNames = {"id", "project_id"}
        )
    }
)
public class Category {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "project_id", nullable = false, insertable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

Case-insensitive uniqueness vẫn do Flyway expression index quản lý.

---

# 17. Tag Entity

```java
@Entity
@Table(
    name = "tags",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_tags_id_project",
            columnNames = {"id", "project_id"}
        )
    }
)
public class Tag {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "project_id", nullable = false, insertable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

Không dùng direct `@ManyToMany` với Document.

---

# 18. Document Entity

```java
@Entity
@Table(
    name = "documents",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_documents_storage_key",
            columnNames = "storage_key"
        ),
        @UniqueConstraint(
            name = "uq_documents_id_project",
            columnNames = {"id", "project_id"}
        )
    }
)
public class Document {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "project_id", nullable = false, insertable = false, updatable = false)
    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;

    @Column(name = "folder_id")
    private UUID folderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "folder_id", referencedColumnName = "id",
                insertable = false, updatable = false),
        @JoinColumn(
            name = "project_id",
            referencedColumnName = "project_id",
            insertable = false,
            updatable = false
        )
    })
    private Folder folder;

    @Column(name = "category_id")
    private UUID categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "category_id", referencedColumnName = "id",
                insertable = false, updatable = false),
        @JoinColumn(
            name = "project_id",
            referencedColumnName = "project_id",
            insertable = false,
            updatable = false
        )
    })
    private Category category;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_kind", nullable = false, length = 20)
    private FileKind fileKind;

    @Column(name = "extension", nullable = false, length = 20)
    private String extension;

    @Column(name = "mime_type", nullable = false, length = 150)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 1024, unique = true)
    private String storageKey;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

`uploadedBy` phải reference `User`, không phải `ProjectMember`, vì file vẫn tồn tại sau khi member rời project.

---

# 19. DocumentTag Composite Key

```java
@Embeddable
public class DocumentTagId implements Serializable {

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "tag_id")
    private UUID tagId;
}
```

`DocumentTagId` phải implement `equals()` và `hashCode()` dựa trên cả hai field.

---

# 20. DocumentTag Entity

`document_tags` chứa thêm `project_id` để enforce:

```text
Document.project_id == Tag.project_id
```

Mapping:

```java
@Entity
@Table(name = "document_tags")
public class DocumentTag {

    @EmbeddedId
    private DocumentTagId id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(
            name = "document_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false
        ),
        @JoinColumn(
            name = "project_id",
            referencedColumnName = "project_id",
            insertable = false,
            updatable = false
        )
    })
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(
            name = "tag_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false
        ),
        @JoinColumn(
            name = "project_id",
            referencedColumnName = "project_id",
            insertable = false,
            updatable = false
        )
    })
    private Tag tag;
}
```

Writable FK state:

```text
id.documentId
id.tagId
projectId
```

Associations `document` và `tag` là read-only views của các cột đó.

---

# 21. Why Not @ManyToMany

Không dùng:

```java
@ManyToMany
```

vì join table không chỉ có `document_id` và `tag_id`, mà còn có `project_id` để bảo vệ cross-project integrity.

Explicit `DocumentTag` giúp:

- Giữ nguyên schema đã chốt.
- Dễ validate project boundary.
- Dễ mở rộng relationship metadata sau này.
- Query rõ ràng hơn.

---

# 22. Bidirectional Relationship Policy

Default:

```text
child → parent only
```

Không cần các collection sau ở phase đầu:

```text
User.sessions
User.documents
Project.members
Project.documents
Project.folders
Folder.children
Category.documents
Tag.documents
```

Lợi ích:

- Giảm recursive graph.
- Giảm N+1 bất ngờ.
- Dễ kiểm soát fetch.
- Dễ viết DTO query.

---

# 23. Fetch Strategy

Explicit:

```java
@ManyToOne(fetch = FetchType.LAZY)
```

thay vì chấp nhận default EAGER của JPA.

DTO/query layer chủ động fetch đúng dữ liệu endpoint cần.

---

# 24. Cascade Strategy

Không dùng `CascadeType.ALL` mặc định.

Ví dụ không được xảy ra:

```text
Delete User → delete Documents
Delete Folder → delete Documents
Delete Category → delete Documents
```

Lifecycle được quản lý bằng Service + DB FK rules đã chốt.

---

# 25. Repository Design Principles

Repositories:

```text
- persistence/query only
- no authorization
- no MinIO
- no email
- no JWT parsing
- no ResponseEntity
- no business transaction orchestration
```

Service layer quyết định business flow.

---

# 26. UserRepository

```java
public interface UserRepository
        extends JpaRepository<User, UUID>,
                JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
```

Email verification updates `User.emailVerifiedAt` through normal `UserRepository` persistence. No `OtpRepository` exists because OTP state is not relational.

`JpaSpecificationExecutor<User>` phục vụ admin filtering:

```text
q
status
systemRole
```

---

# 27. RefreshSessionRepository

```java
public interface RefreshSessionRepository
        extends JpaRepository<RefreshSession, UUID> {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    List<RefreshSession> findAllByUserId(UUID userId);

    long deleteByUserId(UUID userId);

    long deleteByExpiresAtBefore(Instant cutoff);
}
```

Refresh-token rotation chưa được chốt là mandatory Core rule, nên không thiết kế quá mức ở phase này.

---

# 28. ProjectRepository

```java
public interface ProjectRepository
        extends JpaRepository<Project, UUID>,
                JpaSpecificationExecutor<Project> {
}
```

Normal user's project list dựa trên `ProjectMember`, không dựa trên `owner_id`.

---

# 29. ProjectMemberRepository

```java
Optional<ProjectMember> findByProjectIdAndUserId(
    UUID projectId,
    UUID userId
);

boolean existsByProjectIdAndUserId(
    UUID projectId,
    UUID userId
);

Optional<ProjectMember> findByProjectIdAndRole(
    UUID projectId,
    ProjectRole role
);

Page<ProjectMember> findAllByProjectId(
    UUID projectId,
    Pageable pageable
);

long deleteByProjectIdAndUserId(
    UUID projectId,
    UUID userId
);
```

Member list cần tránh N+1 bằng EntityGraph hoặc DTO projection.

Ví dụ:

```java
@EntityGraph(attributePaths = {"user"})
Page<ProjectMember> findAllByProjectId(
    UUID projectId,
    Pageable pageable
);
```

---

# 30. My Projects Query

Có thể đặt query trong `ProjectMemberRepository`:

```java
@Query("""
    select pm.project
    from ProjectMember pm
    where pm.user.id = :userId
      and (:role is null or pm.role = :role)
""")
Page<Project> findProjectsForUser(
    UUID userId,
    ProjectRole role,
    Pageable pageable
);
```

---

# 31. ProjectInvitationRepository

```java
public interface ProjectInvitationRepository
        extends JpaRepository<ProjectInvitation, UUID> {

    Optional<ProjectInvitation> findByTokenHash(String tokenHash);

    Optional<ProjectInvitation> findByIdAndProjectId(
        UUID invitationId,
        UUID projectId
    );

    boolean existsByProjectIdAndEmailAndStatus(
        UUID projectId,
        String email,
        InvitationStatus status
    );
}
```

List endpoint có thể dùng pageable query theo `projectId` và optional `status`.

---

# 32. Invitation Accept Concurrency

Hai request có thể cùng accept một invitation.

Recommended:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select i
    from ProjectInvitation i
    where i.tokenHash = :tokenHash
""")
Optional<ProjectInvitation> findByTokenHashForUpdate(
    String tokenHash
);
```

Trong `@Transactional`:

```text
lock invitation
validate PENDING
validate expiresAt
validate email
validate not already member
create ProjectMember
set invitation ACCEPTED
commit
```

Unique membership constraint là safety net thứ hai.

---

# 33. Invitation Expiration

Có thể có query:

```java
List<ProjectInvitation> findAllByStatusAndExpiresAtBefore(
    InvitationStatus status,
    Instant now
);
```

Nhưng Core v1 chưa bắt buộc scheduled expiration job.

Baseline vẫn là kiểm tra `expiresAt` khi xử lý invitation.

---

# 34. FolderRepository

```java
Optional<Folder> findByIdAndProjectId(
    UUID folderId,
    UUID projectId
);

List<Folder> findAllByProjectId(UUID projectId);

List<Folder> findAllByProjectIdAndParentId(
    UUID projectId,
    UUID parentId
);

boolean existsByProjectIdAndParentIdIsNullAndNameIgnoreCase(
    UUID projectId,
    String name
);

boolean existsByProjectIdAndParentIdAndNameIgnoreCase(
    UUID projectId,
    UUID parentId,
    String name
);

boolean existsByParentId(UUID parentId);
```

Root và child uniqueness được xử lý riêng vì `parent_id = NULL` có semantics khác trong unique constraint.

---

# 35. Folder Rename Excluding Current Folder

Recommended query:

```java
@Query("""
    select count(f) > 0
    from Folder f
    where f.project.id = :projectId
      and lower(f.name) = lower(:name)
      and f.id <> :folderId
      and (
          (:parentId is null and f.parent is null)
          or f.parent.id = :parentId
      )
""")
boolean existsSiblingWithNameExcluding(
    UUID projectId,
    UUID parentId,
    UUID folderId,
    String name
);
```

---

# 36. Folder Cycle Detection

Core v1 baseline:

```text
current = newParent

while current != null:
    if current.id == folderBeingMoved.id:
        reject FOLDER_CYCLE_DETECTED
    current = current.parent
```

Nếu folder tree sau này rất sâu, có thể tối ưu bằng PostgreSQL recursive CTE.

---

# 37. CategoryRepository

```java
public interface CategoryRepository
        extends JpaRepository<Category, UUID> {

    List<Category> findAllByProjectIdOrderByNameAsc(UUID projectId);

    Optional<Category> findByIdAndProjectId(
        UUID categoryId,
        UUID projectId
    );

    boolean existsByProjectIdAndNameIgnoreCase(
        UUID projectId,
        String name
    );
}
```

Rename cần check duplicate name excluding current category ID.

---

# 38. TagRepository

```java
public interface TagRepository
        extends JpaRepository<Tag, UUID> {

    List<Tag> findAllByProjectIdOrderByNameAsc(UUID projectId);

    Optional<Tag> findByIdAndProjectId(
        UUID tagId,
        UUID projectId
    );

    boolean existsByProjectIdAndNameIgnoreCase(
        UUID projectId,
        String name
    );

    List<Tag> findAllByProjectIdAndIdIn(
        UUID projectId,
        Collection<UUID> tagIds
    );
}
```

Khi assign tags:

```text
requested count == found same-project tag count
```

nếu không thì có tag invalid/not found.

---

# 39. DocumentRepository

```java
public interface DocumentRepository
        extends JpaRepository<Document, UUID>,
                JpaSpecificationExecutor<Document> {

    Optional<Document> findByIdAndProjectId(
        UUID documentId,
        UUID projectId
    );

    boolean existsByFolderId(UUID folderId);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByUploadedById(UUID userId);

    long countByProjectId(UUID projectId);
}
```

Không dùng `findAllByProjectId()` cho normal browser nếu project có thể lớn.

---

# 40. Storage-Key Projection

Project hard delete chỉ cần storage keys, không cần full entities.

```java
public interface DocumentStorageKeyProjection {
    UUID getId();
    String getStorageKey();
}
```

```java
@Query("""
    select d.id as id,
           d.storageKey as storageKey
    from Document d
    where d.project.id = :projectId
""")
List<DocumentStorageKeyProjection> findStorageKeysByProjectId(
    UUID projectId
);
```

---

# 41. Document Detail Fetch

Document detail thường cần:

```text
uploadedBy
folder
category
```

Có thể dùng:

```java
@EntityGraph(attributePaths = {
    "uploadedBy",
    "folder",
    "category"
})
@Query("""
    select d
    from Document d
    where d.id = :documentId
""")
Optional<Document> findDetailById(UUID documentId);
```

Tags load riêng qua `DocumentTagRepository`.

---

# 42. DocumentTagRepository

```java
public interface DocumentTagRepository
        extends JpaRepository<DocumentTag, DocumentTagId> {

    List<DocumentTag> findAllByIdDocumentId(UUID documentId);

    long deleteAllByIdDocumentId(UUID documentId);

    boolean existsByIdDocumentIdAndIdTagId(
        UUID documentId,
        UUID tagId
    );
}
```

Load tag cùng lúc bằng EntityGraph hoặc fetch join.

---

# 43. Synchronizing Document Tags

Core v1 có thể dùng transactional replacement:

```text
validate requested tags belong to project
↓
delete existing DocumentTag rows
↓
insert selected DocumentTag rows
```

Tất cả trong cùng `@Transactional` DB transaction.

Với tag set nhỏ, cách này đơn giản và đủ tốt.

---

# 44. Document Metadata Search

REST API supports:

```text
q
folderId
categoryId
tagId
fileKind
uploadedBy
createdFrom
createdTo
page
size
sort
```

`DocumentRepository` extends:

```text
JpaSpecificationExecutor<Document>
```

Recommended component:

```text
DocumentSpecification
```

---

# 45. Search Security Rule

Every document search must include:

```text
document.project.id = projectId
```

Project predicate là bắt buộc.

Không có search document “global” cho normal USER trong Core v1.

---

# 46. Search q Fields

Core v1 searches:

```text
displayName
originalFilename
description
category.name
tag.name
```

Tag match nên dùng subquery/`EXISTS` thay vì bắt buộc map collection từ Document sang DocumentTag.

Lợi ích:

- Không duplicate Document rows.
- Tránh `distinct` count phức tạp.
- Giữ entity graph nhỏ.

---

# 47. Pagination and Sorting

Repository sử dụng:

```text
Pageable
Page<T>
```

API baseline:

```text
page = 0
size = 20
max = 100
```

Allowed sort fields nên whitelist:

```text
displayName
createdAt
updatedAt
sizeBytes
```

Không truyền arbitrary request property thẳng vào Sort.

---

# 48. User Hard Delete Dependency Queries

Trước khi hard delete User, Service cần kiểm tra:

```text
ProjectMemberRepository.existsByUserId(...)
ProjectMemberRepository.existsByUserIdAndRole(... OWNER)
DocumentRepository.existsByUploadedById(...)
ProjectInvitationRepository.existsByInvitedById(...)
```

Không cascade-delete dependencies một cách âm thầm.

---

# 49. Member Removal

Remove member chỉ delete:

```text
ProjectMember row
```

Không delete Document.

Rule được giữ tự nhiên vì:

```text
Document.uploadedBy → User
```

chứ không phải `ProjectMember`.

---

# 50. Category Delete Dependency

Trước delete:

```text
DocumentRepository.existsByCategoryId(categoryId)
```

Nếu true:

```text
CATEGORY_IN_USE
```

DB FK vẫn là lớp bảo vệ cuối.

---

# 51. Folder Delete Dependency

Check:

```text
FolderRepository.existsByParentId(folderId)
DocumentRepository.existsByFolderId(folderId)
```

Nếu một trong hai true:

```text
FOLDER_NOT_EMPTY
```

---

# 52. Tag Delete

Tag được phép delete dù đang dùng.

Database cascade:

```text
Tag → DocumentTag
```

Documents vẫn giữ nguyên.

---

# 53. Pre-check + Database Constraint

Service pre-check giúp trả lỗi đẹp.

Database constraint bảo vệ race condition.

Ví dụ hai request đồng thời tạo tag `jwt`:

```text
Request A check not exists
Request B check not exists
```

Unique index quyết định cuối cùng chỉ một insert thành công.

Service phải translate constraint violation phù hợp nếu race xảy ra.

---

# 54. Locking Strategy

Không thêm pessimistic locks khắp hệ thống.

Explicit lock ban đầu chỉ cần cho:

```text
Invitation accept
```

Các operation khác dựa vào:

```text
transaction
unique constraints
FK constraints
```

cho đến khi có concurrency requirement cụ thể.

---

# 55. Optimistic Locking

Core v1 chưa yêu cầu `@Version`.

Không có collaborative editing hay document version history.

Nếu sau này cần lost-update protection, thêm `@Version` qua design decision riêng.

---

# 56. Repository Return Types

Use:

```text
Optional<Entity> → zero/one
List<Entity>     → bounded collection
Page<Entity>     → large user-facing collection
```

Không dùng:

```text
Optional<List<Entity>>
```

---

# 57. EntityGraph Usage

Use selectively:

```text
member list      → user
document detail  → uploadedBy/folder/category
document tags    → tag
```

Không tạo giant graph load toàn bộ Project.

---

# 58. Open Session in View

DTO mapping nên dựa trên data được fetch có chủ đích.

Recommended production posture:

```text
Open Session in View disabled
```

khi implementation đủ ổn để enforce explicit query boundaries.

Không dựa vào lazy loading tình cờ ở controller.

---

# 59. Cross-Project Safety

Service validates:

```text
folder.project == document.project
category.project == document.project
tag.project == document.project
parent.project == folder.project
```

Database tiếp tục enforce độc lập bằng composite FKs.

Không bỏ `project_id` redundancy trong `document_tags`.

---

# 60. Mapping Summary

| Entity | Main Relationships | Fetch |
|---|---|---|
| User | none required; `emailVerifiedAt` is persistent verification result | — |
| RefreshSession | User | LAZY |
| Project | none required | — |
| ProjectMember | Project, User | LAZY |
| ProjectInvitation | Project, inviter User | LAZY |
| Folder | Project, parent Folder | LAZY |
| Category | Project | LAZY |
| Tag | Project | LAZY |
| Document | Project, User, Folder, Category | LAZY |
| DocumentTag | Document, Tag | LAZY |

---

# 61. Repository Summary

| Repository | Main Purpose |
|---|---|
| UserRepository | authentication/admin user lookup |
| RefreshSessionRepository | refresh session lifecycle |
| ProjectRepository | project persistence/admin search |
| ProjectMemberRepository | membership + project role |
| ProjectInvitationRepository | invitation lifecycle |
| FolderRepository | hierarchy + sibling checks |
| CategoryRepository | project category management |
| TagRepository | project tags + validation |
| DocumentRepository | document CRUD/search/dependency checks |
| DocumentTagRepository | explicit document-tag relation |

---

# 62. Transaction Ownership

Repository methods không own business transactions.

Transactions ở Service:

```text
AuthService.register
ProjectService.createProject
InvitationService.accept
DocumentService.updateMetadata
ProjectMemberService.removeMember
```

Read operations có thể dùng:

```java
@Transactional(readOnly = true)
```

---

# 63. Project Create Flow

```text
ProjectService.createProject
    ↓
ProjectRepository.save(project)
    ↓
ProjectMemberRepository.save(OWNER membership)
```

Một Spring transaction.

Nếu OWNER membership insert fail:

```text
project insert rollback
```

---

# 64. Invitation Accept Flow

```text
InvitationService.accept
    ↓
findByTokenHashForUpdate
    ↓
validate
    ↓
ProjectMemberRepository.exists...
    ↓
ProjectMemberRepository.save
    ↓
Invitation.status = ACCEPTED
    ↓
commit
```

---

# 65. Document Upload Flow

```text
DocumentService.upload
    ↓
validate project access
    ↓
validate folder/category/tags
    ↓
StorageService.upload
    ↓
DocumentRepository.save
    ↓
DocumentTagRepository.saveAll
```

Nếu DB fail sau MinIO upload:

```text
StorageService.delete
```

Repository không gọi StorageService.

---

# 66. Document Update Flow

```text
DocumentRepository.findDetailById
    ↓
DocumentAuthorizationService
    ↓
validate folder/category/tags
    ↓
modify Document
    ↓
DocumentTagRepository.deleteAllByIdDocumentId
    ↓
DocumentTagRepository.saveAll
```

DB changes chạy trong một transaction.

---

# 67. Project Delete Flow

Không load full Document graph chỉ để lấy storage key.

Use:

```text
DocumentRepository.findStorageKeysByProjectId
```

Sau đó:

```text
StorageService.delete objects
ProjectRepository.delete(project)
```

DB cascade relational data theo Physical Design.

---

# 68. Testing Entity Mapping

Integration tests phải chạy trên real PostgreSQL behavior qua Testcontainers.

Important tests:

```text
ProjectMember unique project/user
single OWNER partial index
Folder parent same project
Document folder same project
Document category same project
DocumentTag document/tag same project
pending invitation unique project/email
category/tag case-insensitive uniqueness
folder root/child case-insensitive uniqueness
```

Không dùng H2 làm database integration chính cho các constraint này.

---

# 69. Repository Tests

Cover:

```text
User email lookup
Project membership lookup
Invitation pessimistic lookup
Folder sibling-name checks
Category in-use query
Folder non-empty queries
Tag project-scoped lookup
Document storage-key projection
Document metadata filtering
DocumentTag loading with Tag
```

---

# 70. Security Boundary Reminder

Repository lookup không có nghĩa user được authorize.

```text
DocumentRepository.findById(...)
```

vẫn phải đi qua:

```text
DocumentAuthorizationService
```

Controller không được inject Repository trực tiếp cho normal operations.

---

# 71. Deliberately Not Added

Không thêm:

```text
DocumentVersion
AuditLog
AI entities
Vector entities
Chat entities
Quota entities
ShareLink
Favorite
Comment
Approval
OtpEntity / OtpRepository
```

Không có repository cho các feature này trong Core v1. OTP là Core v1 feature nhưng state của OTP được lưu qua Redis `OtpStore`, không phải JPA/PostgreSQL entity.

---

# 72. Implementation Decisions Still Open

Cố ý để mở:

```text
exact Spring Boot version
exact Hibernate version
GenerationType.UUID vs @UuidGenerator
Lombok usage
MapStruct usage
OSIV final setting
refresh-token rotation
scheduled invitation expiration cleanup
advanced custom repository implementation for search
optimistic locking
```

Không coi các điểm này là business requirement đã chốt.

---

# 73. Recommended Baseline

```text
Spring Data JPA
PostgreSQL
Flyway
application-side UUID generation
Instant timestamps
EnumType.STRING
feature-local repositories
LAZY relationships
explicit DTOs
explicit DocumentTag entity
JpaSpecificationExecutor for dynamic search
pessimistic lock only for invitation acceptance initially
PostgreSQL Testcontainers
Redis Testcontainer for OTP integration at service/API layer
Hibernate ddl-auto=validate
```

---

# 74. Definition of Done

Phase này hoàn thành khi:

```text
all 10 physical tables have entity mappings

User maps `email_verified_at` while OTP state itself remains outside JPA in Redis

composite project-isolation FKs remain represented

DocumentTag uses explicit composite-key entity

OWNER is not duplicated on Project

Document uploader references User, not ProjectMember

no dangerous broad cascade exists

repositories are feature-local

document search has a dynamic query strategy

repository queries support Core REST use cases

database remains authoritative for advanced constraints

repository layer contains no authorization or infrastructure logic
```

---

# 75. Next Phase

Recommended next phase:

```text
JPA Entity Mapping + Repository Design
        ↓
Spring Security + JWT Design
        ↓
Service Layer Detailed Design
        ↓
MinIO Integration Design
        ↓
Exception Handling Design
        ↓
OpenAPI Configuration
        ↓
Testing Strategy
        ↓
Implementation Plan
```

Immediate next artifact:

```text
KBase Core v1 – Spring Security + JWT Design
```

Nội dung cần chốt ở phase đó:

```text
SecurityFilterChain
JWT claims
access-token lifecycle
refresh-session lifecycle
password hashing
authentication principal
401 vs 403 handling
ADMIN override
project authorization integration
document ownership authorization
disabled-account behavior
logout/session revocation
```

mà không thay đổi permission model hiện tại.
