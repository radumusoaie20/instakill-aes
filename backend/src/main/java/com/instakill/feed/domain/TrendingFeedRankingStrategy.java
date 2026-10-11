package com.instakill.feed.domain;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;

@Component 
public class TrendingFeedRankingStrategy implements FeedRankingStrategy{

    @Override
    public FeedSort sort() {
     
        return FeedSort.TRENDING;
        
    }

    @Override
    public List<Post> fetch(PostRepository repository, int page, int size) {

        return repository.findTrending(page, size);

    }
    
}
