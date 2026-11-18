package com.instakill.infrastructure.persistence.repository;

import com.instakill.infrastructure.persistence.entity.LikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface JpaLikeRepository extends JpaRepository<LikeEntity, UUID> {

    Optional<LikeEntity> findByPostIdAndUserId(UUID postId, UUID userId);

    long countByPostId(UUID postId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    @Query("SELECT l.postId FROM LikeEntity l WHERE l.userId = :userId AND l.postId IN :postIds")
    Set<UUID> findLikedPostIdsByUserIdAndPostIdIn(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);
}
