package com.kbase.tag.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.tag.entity.Tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Project-scoped tag queries. */
public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findAllByProjectIdOrderByNameAsc(UUID projectId);

    Optional<Tag> findByIdAndProjectId(UUID tagId, UUID projectId);

    boolean existsByProjectIdAndNameIgnoreCase(UUID projectId, String name);

    boolean existsByProjectIdAndNameIgnoreCaseAndIdNot(
            UUID projectId, String name, UUID tagId);

    /**
     * Case-insensitive substring match over the tag name. The query value is
     * expected to arrive with LIKE wildcards already escaped, so a client
     * supplied percent or underscore matches literally.
     */
    @Query("""
            select t
            from Tag t
            where t.project.id = :projectId
              and lower(t.name) like lower(concat('%', :query, '%')) escape '\\'
            order by lower(t.name) asc
            """)
    List<Tag> findAllByProjectIdAndNameLikeIgnoreCaseOrderByNameAsc(
            @Param("projectId") UUID projectId, @Param("query") String escapedQuery);

    List<Tag> findAllByProjectIdAndIdIn(UUID projectId, Collection<UUID> tagIds);
}
