package com.kbase.category.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kbase.category.entity.Category;

import org.springframework.data.jpa.repository.JpaRepository;

/** Project-scoped category queries. */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByProjectIdOrderByNameAsc(UUID projectId);

    Optional<Category> findByIdAndProjectId(UUID categoryId, UUID projectId);

    boolean existsByProjectIdAndNameIgnoreCase(UUID projectId, String name);

    boolean existsByProjectIdAndNameIgnoreCaseAndIdNot(
            UUID projectId, String name, UUID categoryId);
}
