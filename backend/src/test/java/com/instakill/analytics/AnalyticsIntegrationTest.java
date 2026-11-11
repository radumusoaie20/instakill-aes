package com.instakill.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instakill.interaction.application.InteractionService;
import com.instakill.post.application.PostService;
import com.instakill.post.domain.Post;
import com.instakill.user.application.AuthService;
import com.instakill.user.domain.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private PostService postService;

    @Autowired
    private InteractionService interactionService;

    @Autowired
    private ObjectMapper objectMapper;

    private User primaryUser;
    private User peerUser;
    private Cookie authCookie;

    @BeforeEach
    void setUp() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        primaryUser = authService.register(
                "analyst_" + uniqueSuffix,
                "analyst_" + uniqueSuffix + "@example.com",
                "password"
        ).user();

        peerUser = authService.register(
                "peer_" + uniqueSuffix,
                "peer_" + uniqueSuffix + "@example.com",
                "password"
        ).user();

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .param("usernameOrEmail", primaryUser.username())
                        .param("password", "password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        authCookie = loginResult.getResponse().getCookie("ACCESS_TOKEN");
        assertThat(authCookie).isNotNull();
    }

    @Test
    void testAnalyticsEndpointCalculatesCorrectStatistics() throws Exception {
        // Create 1 photo post and 1 video post
        Post photoPost = postService.createPost(primaryUser.id(), "Photo Post Content", "https://example.com/img.jpg");
        Post videoPost = postService.createPost(primaryUser.id(), "Video Post Content", "https://example.com/vid.mp4");

        // Add 2 likes to photo post, 1 comment
        interactionService.toggleLike(photoPost.id(), peerUser.id());
        interactionService.addComment(photoPost.id(), peerUser.id(), "Great picture!");

        // Add 1 comment to video post
        interactionService.addComment(videoPost.id(), peerUser.id(), "Cool clip!");

        // 1. Test REST API /api/analytics/me
        MvcResult restResult = mockMvc.perform(get("/api/analytics/me").cookie(authCookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(restResult.getResponse().getContentAsString());
        assertThat(json.get("userId").asText()).isEqualTo(primaryUser.id().toString());

        // Check weekly stats
        JsonNode weeklyStats = json.get("weeklyStats");
        assertThat(weeklyStats.isArray()).isTrue();
        assertThat(weeklyStats.size()).isGreaterThanOrEqualTo(1);
        assertThat(weeklyStats.get(0).get("posts").asLong()).isEqualTo(2);

        // Check top 5 posts
        JsonNode topPosts = json.get("topPosts");
        assertThat(topPosts.isArray()).isTrue();
        assertThat(topPosts.size()).isEqualTo(2);
        // Photo post has 1 like + 1 comment = 2 engagement, video post has 0 likes + 1 comment = 1 engagement
        assertThat(topPosts.get(0).get("id").asText()).isEqualTo(photoPost.id().toString());
        assertThat(topPosts.get(0).get("engagement").asLong()).isEqualTo(2);
        assertThat(topPosts.get(1).get("id").asText()).isEqualTo(videoPost.id().toString());
        assertThat(topPosts.get(1).get("engagement").asLong()).isEqualTo(1);

        // Check like-to-comment ratio: 1 like, 2 comments -> 1 / 2 = 0.5
        JsonNode ratio = json.get("likeCommentRatio");
        assertThat(ratio.get("totalLikes").asLong()).isEqualTo(1);
        assertThat(ratio.get("totalComments").asLong()).isEqualTo(2);
        assertThat(ratio.get("ratio").asDouble()).isEqualTo(0.5);

        // Check media likes stats: Photo avg likes = 1.0, Video avg likes = 0.0
        JsonNode mediaStats = json.get("mediaLikesStats");
        assertThat(mediaStats.get("photoAvgLikes").asDouble()).isEqualTo(1.0);
        assertThat(mediaStats.get("photoPostCount").asLong()).isEqualTo(1);
        assertThat(mediaStats.get("videoAvgLikes").asDouble()).isEqualTo(0.0);
        assertThat(mediaStats.get("videoPostCount").asLong()).isEqualTo(1);

        // Check ranking
        JsonNode ranking = json.get("engagementRanking");
        assertThat(ranking.get("rank").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(ranking.get("totalUsers").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(ranking.get("topPercentile").asDouble()).isBetween(0.0, 100.0);

        // 2. Test MVC view /analytics
        mockMvc.perform(get("/analytics").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("User Analytics")))
                .andExpect(content().string(containsString("Global Engagement Ranking")))
                .andExpect(content().string(containsString("Like-to-Comment Ratio")))
                .andExpect(content().string(containsString("Avg Likes by Media Type")))
                .andExpect(content().string(containsString("Top 5 Posts by Engagement")))
                .andExpect(content().string(containsString("Activity by Week of Creation")));
    }
}
