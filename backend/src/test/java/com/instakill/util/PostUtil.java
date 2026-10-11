package com.instakill.util;

import java.time.Instant;
import java.util.UUID;

import com.instakill.post.domain.Post;


public class PostUtil {
    

    public static Post post(Instant createdAt, long likes, long comments) {
        UUID id = UUID.randomUUID();
        return new Post(id, UUID.randomUUID(), "content", null, createdAt, createdAt, likes, comments);
    }

}
