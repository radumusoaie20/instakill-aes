package com.instakill.feed.domain;

import java.util.List;

import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;

public interface FeedRankingStrategy {

    FeedSort sort();

    List<Post> fetch(PostRepository repository, int page, int size);
    
}
