package com.instakill.feed.domain;

import java.util.List;

import org.springframework.stereotype.Component;

import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;

@Component 
public class NewestFeedRankingStrategy implements FeedRankingStrategy {

    @Override
    public FeedSort sort() {
        return FeedSort.NEW;
    }

    @Override
    public List<Post> fetch(PostRepository repository, int page, int size) {
       
        return repository.findLatest(page, size);

    }

    
    
}
