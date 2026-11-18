package com.instakill.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instakill.interaction.application.InteractionService;
import com.instakill.post.application.PostService;
import com.instakill.post.domain.Post;
import com.instakill.user.application.AuthService;
import com.instakill.user.application.UserService;
import com.instakill.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrencyAndBatchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private PostService postService;

    @Autowired
    private InteractionService interactionService;

    @Test
    void batchFeedRetrievalResolvesAuthorsAndLikedStatus() throws Exception {
        AuthService.AuthResult user1Auth = authService.register("batchuser1", "batch1@example.com", "password");
        AuthService.AuthResult user2Auth = authService.register("batchuser2", "batch2@example.com", "password");

        Post post1 = postService.createPost(user1Auth.user().id(), "Post 1 by user 1", null);
        Post post2 = postService.createPost(user2Auth.user().id(), "Post 2 by user 2", null);

        // User 1 likes post 1 only
        interactionService.toggleLike(post1.id(), user1Auth.user().id());

        // Batch test on UserService
        var authorMap = userService.getUsersByIds(Set.of(user1Auth.user().id(), user2Auth.user().id()));
        assertThat(authorMap).hasSize(2);
        assertThat(authorMap.get(user1Auth.user().id()).username()).isEqualTo("batchuser1");
        assertThat(authorMap.get(user2Auth.user().id()).username()).isEqualTo("batchuser2");

        // Batch test on InteractionService
        Set<UUID> liked = interactionService.getLikedPostIds(user1Auth.user().id(), List.of(post1.id(), post2.id()));
        assertThat(liked).containsExactly(post1.id());

        // Call /api/feed with user1 token
        MvcResult feedResult = mockMvc.perform(get("/api/feed")
                        .header("Authorization", "Bearer " + user1Auth.token())
                        .param("sort", "new"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(feedResult.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();

        boolean post1Checked = false;
        boolean post2Checked = false;
        for (JsonNode item : json) {
            if (item.get("id").asText().equals(post1.id().toString())) {
                assertThat(item.get("author").get("username").asText()).isEqualTo("batchuser1");
                assertThat(item.get("likedByCurrentUser").asBoolean()).isTrue();
                post1Checked = true;
            } else if (item.get("id").asText().equals(post2.id().toString())) {
                assertThat(item.get("author").get("username").asText()).isEqualTo("batchuser2");
                assertThat(item.get("likedByCurrentUser").asBoolean()).isFalse();
                post2Checked = true;
            }
        }
        assertThat(post1Checked).isTrue();
        assertThat(post2Checked).isTrue();
    }

    @Test
    void concurrentCommentsAndLikesMaintainState() throws Exception {
        AuthService.AuthResult author = authService.register("concurrenthost", "host@example.com", "password");
        Post post = postService.createPost(author.user().id(), "Concurrency test post", null);

        int numUsers = 8;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < numUsers; i++) {
            AuthService.AuthResult u = authService.register("cuser" + i, "cuser" + i + "@example.com", "password");
            users.add(u.user());
        }

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(numUsers);
        List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

        // Concurrently add comments and likes from different users
        for (int i = 0; i < numUsers; i++) {
            final User u = users.get(i);
            final int index = i;
            executor.submit(() -> {
                try {
                    interactionService.addComment(post.id(), u.id(), "Comment from user " + index);
                    interactionService.toggleLike(post.id(), u.id());
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();
        if (!errors.isEmpty()) {
            errors.get(0).printStackTrace();
        }
        assertThat(errors).isEmpty();

        Post updatedPost = postService.getPost(post.id());
        assertThat(updatedPost.commentCount()).isEqualTo(numUsers);
        assertThat(updatedPost.likeCount()).isEqualTo(numUsers);
    }
}
