package com.instakill.analytics.domain;

public record MediaLikesStats(
        double photoAvgLikes,
        long photoPostCount,
        double videoAvgLikes,
        long videoPostCount
) {}
