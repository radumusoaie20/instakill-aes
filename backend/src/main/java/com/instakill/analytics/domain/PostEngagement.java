package com.instakill.analytics.domain;

import java.time.Instant;
import java.util.UUID;

public record PostEngagement(
        UUID id,
        String content,
        String imageUrl,
        Instant createdAt,
        long likeCount,
        long commentCount,
        long engagement
) {}
