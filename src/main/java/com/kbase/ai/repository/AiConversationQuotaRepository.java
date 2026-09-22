package com.kbase.ai.repository;

import java.util.Objects;
import java.util.UUID;

import com.kbase.ai.entity.AiConversation;
import com.kbase.user.entity.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

/**
 * Persistence primitives for the per-user conversation quota.
 *
 * <p>The caller must invoke {@link #lockUserForConversationQuota(UUID)} and
 * {@link #countConversations(UUID, UUID)} in the same transaction before inserting
 * through {@link AiConversationRepository}. Locking the durable user row serializes
 * quota checks for that user across projects and prevents count-then-insert races.</p>
 */
@Repository
public class AiConversationQuotaRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public User lockUserForConversationQuota(UUID userId) {
        Objects.requireNonNull(userId, "userId is mandatory");
        try {
            return entityManager.createQuery(
                            "select u from User u where u.id = :userId", User.class)
                    .setParameter("userId", userId)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getSingleResult();
        } catch (NoResultException exception) {
            return null;
        }
    }

    public long countConversations(UUID projectId, UUID userId) {
        Objects.requireNonNull(projectId, "projectId is mandatory");
        Objects.requireNonNull(userId, "userId is mandatory");
        return entityManager.createQuery(
                        "select count(c) from " + AiConversation.class.getSimpleName()
                                + " c where c.projectId = :projectId"
                                + " and c.createdByUserId = :userId", Long.class)
                .setParameter("projectId", projectId)
                .setParameter("userId", userId)
                .getSingleResult();
    }
}
