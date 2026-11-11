package com.instakill.analytics.domain;

public record LikeCommentRatio(
        long totalLikes,
        long totalComments,
        double ratio
) {}
