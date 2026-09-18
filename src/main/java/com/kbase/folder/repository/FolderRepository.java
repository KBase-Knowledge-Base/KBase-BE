package com.kbase.folder.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.folder.entity.Folder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Project-scoped folder hierarchy queries. */
public interface FolderRepository extends JpaRepository<Folder, UUID> {

    Optional<Folder> findByIdAndProjectId(UUID folderId, UUID projectId);

    List<Folder> findAllByProjectId(UUID projectId);

    List<Folder> findAllByProjectIdAndParentId(UUID projectId, UUID parentId);

    boolean existsByProjectIdAndParentIdIsNullAndNameIgnoreCase(UUID projectId, String name);

    boolean existsByProjectIdAndParentIdIsNullAndNameIgnoreCaseAndIdNot(
            UUID projectId, String name, UUID folderId);

    boolean existsByProjectIdAndParentIdAndNameIgnoreCase(
            UUID projectId, UUID parentId, String name);

    boolean existsByProjectIdAndParentIdAndNameIgnoreCaseAndIdNot(
            UUID projectId, UUID parentId, String name, UUID folderId);

    boolean existsByParentId(UUID parentId);

    boolean existsByParentIdAndProjectId(UUID parentId, UUID projectId);

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
            @Param("projectId") UUID projectId,
            @Param("parentId") UUID parentId,
            @Param("folderId") UUID folderId,
            @Param("name") String name);
}
