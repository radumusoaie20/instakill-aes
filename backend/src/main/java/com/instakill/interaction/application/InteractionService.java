package com.instakill.interaction.application;

import com.instakill.common.clock.Clock;
import com.instakill.common.error.NotFoundException;
import com.instakill.common.id.IdGenerator;
import com.instakill.interaction.domain.*;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class InteractionService {

    private static final Logger log = LoggerFactory.getLogger(InteractionService.class);
    private static final Object WRITE_LOCK = new Object();

    private final DomainEventPublisher eventPublisher;
    private final CommentRepository comments;
    private final LikeRepository likes;
    private final PostRepository posts;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public InteractionService(CommentRepository comments,
                              LikeRepository likes,
                              PostRepository posts,
                              IdGenerator idGenerator,
                              Clock clock,
                              DomainEventPublisher publisher) {
        this(comments, likes, posts, idGenerator, clock, publisher, null);
    }

    @Autowired
    public InteractionService(CommentRepository comments,
                              LikeRepository likes,
                              PostRepository posts,
                              IdGenerator idGenerator,
                              Clock clock,
                              DomainEventPublisher publisher,
                              @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.comments = comments;
        this.likes = likes;
        this.posts = posts;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.eventPublisher = publisher;
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
    }

    public Comment addComment(UUID postId, UUID authorId, String content) {
        synchronized (WRITE_LOCK) {
            if (transactionTemplate != null) {
                return transactionTemplate.execute(status -> doAddComment(postId, authorId, content));
            } else {
                return doAddComment(postId, authorId, content);
            }
        }
    }

    private Comment doAddComment(UUID postId, UUID authorId, String content) {
        Post post = posts.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        Instant now = clock.now();
        Comment comment = new Comment(idGenerator.generate(), postId, authorId, content, now);
        Comment saved = comments.save(comment);
        long commentCount = comments.countByPostId(postId);
        Post latestPost = posts.findById(postId).orElse(post);
        posts.save(latestPost.withCommentCount(commentCount).withUpdatedAt(now));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Comment> listComments(UUID postId, int page, int size) {
        posts.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        return comments.findByPostId(postId, page, size);
    }

    public ToggleLikeResult toggleLike(UUID postId, UUID userId) {
        synchronized (WRITE_LOCK) {
            if (transactionTemplate != null) {
                return transactionTemplate.execute(status -> doToggleLike(postId, userId));
            } else {
                return doToggleLike(postId, userId);
            }
        }
    }

    private ToggleLikeResult doToggleLike(UUID postId, UUID userId) {
        Post post = posts.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        Instant now = clock.now();
        return likes.findByPostIdAndUserId(postId, userId)
                .map(existing -> {
                    likes.deleteById(existing.id());
                    long likeCount = likes.countByPostId(postId);
                    Post latestPost = posts.findById(postId).orElse(post);
                    posts.save(latestPost.withLikeCount(likeCount).withUpdatedAt(now));
                    return new ToggleLikeResult(false, likeCount);
                })
                .orElseGet(() -> {
                    Like like = new Like(idGenerator.generate(), postId, userId, now);
                    try {
                        likes.save(like);
                    } catch (Exception e) {
                        log.warn("Like already registered for post {} and user {}", postId, userId);
                    }
                    long likeCount = likes.countByPostId(postId);
                    Post latestPost = posts.findById(postId).orElse(post);
                    posts.save(latestPost.withLikeCount(likeCount).withUpdatedAt(now));

                    eventPublisher.publish(new LikeCreatedEvent(postId, userId, now));

                    return new ToggleLikeResult(true, likeCount);
                });
    }

    @Transactional(readOnly = true)
    public boolean hasUserLiked(UUID postId, UUID userId) {
        return likes.existsByPostIdAndUserId(postId, userId);
    }

    @Transactional(readOnly = true)
    public Set<UUID> getLikedPostIds(UUID userId, Collection<UUID> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return likes.findLikedPostIds(userId, postIds);
    }

    public record ToggleLikeResult(boolean liked, long likeCount) {
    }
}
