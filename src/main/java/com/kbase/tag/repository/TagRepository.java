package com.kbase.tag.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.tag.entity.Tag;

import org.springframework.data.jpa.repository.JpaRepository;

/** Project-scoped tag queries. */
public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findAllByProjectIdOrderByNameAsc(UUID projectId);

    Optional<Tag> findByIdAndProjectId(UUID tagId, UUID projectId);

    boolean existsByProjectIdAndNameIgnoreCase(UUID projectId, String name);

    boolean existsByProjectIdAndNameIgnoreCaseAndIdNot(
            UUID projectId, String name, UUID tagId);

    List<Tag> findAllByProjectIdAndNameContainingIgnoreCaseOrderByNameAsc(
            UUID projectId, String name);

    List<Tag> findAllByProjectIdAndIdIn(UUID projectId, Collection<UUID> tagIds);
}
