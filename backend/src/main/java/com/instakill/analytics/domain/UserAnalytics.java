package com.instakill.analytics.domain;

import java.util.List;
import java.util.UUID;

public record UserAnalytics(
        UUID userId,
        List<WeeklyStats> weeklyStats,
        List<PostEngagement> topPosts,
        LikeCommentRatio likeCommentRatio,
        MediaLikesStats mediaLikesStats,
        EngagementRanking engagementRanking
) {}
