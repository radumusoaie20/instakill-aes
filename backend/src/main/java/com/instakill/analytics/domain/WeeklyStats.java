package com.instakill.analytics.domain;

public record WeeklyStats(
        String week,
        long posts,
        long likes,
        long comments
) {}
