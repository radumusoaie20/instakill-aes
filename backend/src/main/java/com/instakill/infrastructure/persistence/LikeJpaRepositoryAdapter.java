package com.instakill.infrastructure.persistence;

import com.instakill.common.mapping.LikeMapper;
import com.instakill.infrastructure.persistence.repository.JpaLikeRepository;
import com.instakill.interaction.domain.Like;
import com.instakill.interaction.domain.LikeRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class LikeJpaRepositoryAdapter implements LikeRepository {

    private final JpaLikeRepository repository;

    public LikeJpaRepositoryAdapter(JpaLikeRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Like> findByPostIdAndUserId(UUID postId, UUID userId) {
        return repository.findByPostIdAndUserId(postId, userId).map(LikeMapper::toDomain);
    }

    @Override
    @Transactional
    public Like save(Like like) {
        return LikeMapper.toDomain(repository.save(LikeMapper.toEntity(like)));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public long countByPostId(UUID postId) {
        return repository.countByPostId(postId);
    }

    @Override
    public boolean existsByPostIdAndUserId(UUID postId, UUID userId) {
        return repository.existsByPostIdAndUserId(postId, userId);
    }

    @Override
    public Set<UUID> findLikedPostIds(UUID userId, Collection<UUID> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return repository.findLikedPostIdsByUserIdAndPostIdIn(userId, postIds);
    }
}
