package com.instakill.feed.application;

import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import com.instakill.util.FeedRankingStrategiesUtil;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static com.instakill.util.PostUtil.post;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.when;

public class FeedRankingStrategyTest {

    private final PostRepository repository = Mockito.mock(PostRepository.class);
    private final FeedService service = new FeedService(repository, FeedRankingStrategiesUtil.getStrategies());

    @Test
    void defaultsToNewWhenSortIsNull() {
        List<Post> posts = List.of(post(Instant.now(), 1, 1));
        when(repository.findLatest(0, 5)).thenReturn(posts);

        List<Post> result = service.listFeed(null, 0, 5);

        assertThat(result).hasSize(1);
        assertThat(result).isEqualTo(posts);
    }
    
}
