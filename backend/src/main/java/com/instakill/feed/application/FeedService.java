package com.instakill.feed.application;

import com.instakill.feed.domain.FeedSort;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.instakill.feed.domain.FeedRankingStrategy;

@Service
public class FeedService {

    private final List<FeedRankingStrategy> strategies;

    private final PostRepository posts;

    private final Map<FeedSort, FeedRankingStrategy> strategyMap;

    public FeedService(PostRepository posts,
                       List<FeedRankingStrategy> strategies
    ) {
        this.posts = posts;
        this.strategies = strategies;

        strategyMap = new HashMap<>();

        strategies.forEach(
            str -> strategyMap.put(str.sort(), str)
        );

    }

    public List<Post> listFeed(FeedSort sort, int page, int size) {

        if(sort == null) {
            sort = FeedSort.NEW;
        }

        return strategyMap.get(sort).fetch(posts, page, size);
        
    }
    
}
