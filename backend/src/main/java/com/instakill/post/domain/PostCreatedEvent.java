package com.instakill.post.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PostCreatedEvent(
        UUID postId,
        UUID authorId,
        String username,
        Instant createdAt
) {
    public PostCreatedEvent {
        Objects.requireNonNull(postId, "postId must not be null");
        Objects.requireNonNull(authorId, "authorId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
