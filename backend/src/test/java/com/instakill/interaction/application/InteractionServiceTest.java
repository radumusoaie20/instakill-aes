package com.instakill.interaction.application;

import com.instakill.common.clock.Clock;
import com.instakill.common.id.IdGenerator;
import com.instakill.interaction.domain.*;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InteractionServiceTest {

    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final Clock clock = mock(Clock.class);

    private InteractionService service;
    private final UUID postId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Post post = new Post(postId, UUID.randomUUID(), "content", null, Instant.now(), Instant.now(), 0, 0);

    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);

    @BeforeEach
    void setUp() {
        service = new InteractionService(commentRepository, likeRepository, postRepository, idGenerator, clock, eventPublisher);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(clock.now()).thenReturn(Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void togglingAddsLikeWhenAbsent() {
        UUID likeId = UUID.randomUUID();
        when(idGenerator.generate()).thenReturn(likeId);
        when(likeRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());
        when(likeRepository.countByPostId(postId)).thenReturn(1L);

        doNothing().when(eventPublisher).publish(any());

        InteractionService.ToggleLikeResult result = service.toggleLike(postId, userId);

        assertThat(result).isEqualTo(new InteractionService.ToggleLikeResult(true, 1L));
        verify(likeRepository).save(any(Like.class));
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        assertThat(postCaptor.getValue().likeCount()).isEqualTo(1);
        assertThat(postCaptor.getValue().updatedAt()).isEqualTo(clock.now());

        // It should've called publish for the LikeCreatedEvent upon refactoring

        verify(eventPublisher).publish(
                new LikeCreatedEvent(
                        postId,
                        userId,
                        Instant.parse("2024-01-01T00:00:00Z")
                )
        );
    }

    @Test
    void togglingRemovesLikeWhenPresent() {
        UUID likeId = UUID.randomUUID();
        Like existing = new Like(likeId, postId, userId, Instant.now());
        when(likeRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.of(existing));
        when(likeRepository.countByPostId(postId)).thenReturn(0L);

        doNothing().when(eventPublisher).publish(any());

        InteractionService.ToggleLikeResult result = service.toggleLike(postId, userId);

        assertThat(result).isEqualTo(new InteractionService.ToggleLikeResult(false, 0L));
        verify(likeRepository).deleteById(likeId);
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        assertThat(postCaptor.getValue().likeCount()).isZero();

        // No call for eventPublisher when removing a like
        verify(eventPublisher, never()).publish(any());

    }
}
