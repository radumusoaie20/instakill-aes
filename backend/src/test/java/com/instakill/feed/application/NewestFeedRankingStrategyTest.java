package com.instakill.feed.application;

import com.instakill.feed.domain.NewestFeedRankingStrategy;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static com.instakill.util.PostUtil.post;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.when;

public class NewestFeedRankingStrategyTest {

    private final PostRepository repository = Mockito.mock(PostRepository.class);
    private final NewestFeedRankingStrategy strategy = new NewestFeedRankingStrategy();

    @Test
    void returnsNewestFeedInCorrectOrder() {
        List<Post> posts = List.of(
                post(Instant.now().minusSeconds(10), 1, 1),
                post(Instant.now().minusSeconds(20), 2, 2)
        );
        when(repository.findLatest(0, 2)).thenReturn(posts);

        List<Post> result = strategy.fetch(repository, 0, 2);

        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(posts);
    }

}
