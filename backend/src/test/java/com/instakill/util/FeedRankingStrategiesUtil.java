package com.instakill.util;

import com.instakill.feed.domain.FeedRankingStrategy;
import com.instakill.feed.domain.NewestFeedRankingStrategy;
import com.instakill.feed.domain.TrendingFeedRankingStrategy;

import java.util.List;

public class FeedRankingStrategiesUtil {

    public static List<FeedRankingStrategy> getStrategies() {

        return List.of(
                new NewestFeedRankingStrategy(),
                new TrendingFeedRankingStrategy());

    }

}
