package com.instakill.analytics.domain;

public record EngagementRanking(
        double userAvgEngagement,
        long rank,
        long totalUsers,
        double topPercentile
) {}
