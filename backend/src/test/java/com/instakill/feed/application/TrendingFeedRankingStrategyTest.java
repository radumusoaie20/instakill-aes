package com.instakill.feed.application;

import com.instakill.feed.domain.TrendingFeedRankingStrategy;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;
import static com.instakill.util.PostUtil.post;

class TrendingFeedRankingStrategyTest {

    private final PostRepository repository = Mockito.mock(PostRepository.class);
    private final TrendingFeedRankingStrategy strategy = new TrendingFeedRankingStrategy();

    @Test
    void sortsTrendingFeedByLikesThenCommentsThenRecency() {
        Instant now = Instant.now();
        List<Post> posts = List.of(
                post(now.minusSeconds(60), 2, 5),
                post(now.minusSeconds(30), 5, 1),
                post(now.minusSeconds(10), 5, 3)
        );
        when(repository.findLatest(0, 10)).thenReturn(posts);

        List<Post> result = strategy.fetch(repository,0, 2);

        assertThat(result)
                .hasSize(2)
                .extracting(Post::likeCount, Post::commentCount)
                .containsExactly(
                        tuple(5L, 3L),
                        tuple(5L, 1L)
                );
    }


}
