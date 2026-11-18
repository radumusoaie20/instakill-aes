package com.instakill.analytics.infrastructure.persistence;

import com.instakill.analytics.domain.AnalyticsRepository;
import com.instakill.analytics.domain.EngagementRanking;
import com.instakill.analytics.domain.LikeCommentRatio;
import com.instakill.analytics.domain.MediaLikesStats;
import com.instakill.analytics.domain.PostEngagement;
import com.instakill.analytics.domain.UserAnalytics;
import com.instakill.analytics.domain.WeeklyStats;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class SqlAnalyticsRepository implements AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SqlAnalyticsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserAnalytics getUserAnalytics(UUID userId) {
        String userIdStr = userId.toString();
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userIdStr);

        List<WeeklyStats> weeklyStats = fetchWeeklyStats(params);
        List<PostEngagement> topPosts = fetchTopPosts(params);
        LikeCommentRatio likeCommentRatio = fetchLikeCommentRatio(params);
        MediaLikesStats mediaLikesStats = fetchMediaLikesStats(params);
        EngagementRanking engagementRanking = fetchEngagementRanking(params);

        return new UserAnalytics(
                userId,
                weeklyStats,
                topPosts,
                likeCommentRatio,
                mediaLikesStats,
                engagementRanking
        );
    }

    private List<WeeklyStats> fetchWeeklyStats(MapSqlParameterSource params) {
        String sql = """
                SELECT 
                    strftime('%Y-W%W', datetime(created_at / 1000, 'unixepoch')) AS week,
                    COUNT(id) AS post_count,
                    COALESCE(SUM(like_count), 0) AS total_likes,
                    COALESCE(SUM(comment_count), 0) AS total_comments
                FROM posts
                WHERE author_id = :userId
                GROUP BY week
                ORDER BY week DESC
                """;

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new WeeklyStats(
                rs.getString("week") != null ? rs.getString("week") : "Unknown",
                rs.getLong("post_count"),
                rs.getLong("total_likes"),
                rs.getLong("total_comments")
        ));
    }

    private List<PostEngagement> fetchTopPosts(MapSqlParameterSource params) {
        String sql = """
                SELECT 
                    id,
                    content,
                    image_url,
                    created_at,
                    like_count,
                    comment_count,
                    (like_count + comment_count) AS engagement
                FROM posts
                WHERE author_id = :userId
                ORDER BY engagement DESC, created_at DESC
                LIMIT 5
                """;

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new PostEngagement(
                UUID.fromString(rs.getString("id")),
                rs.getString("content"),
                rs.getString("image_url"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                rs.getLong("like_count"),
                rs.getLong("comment_count"),
                rs.getLong("engagement")
        ));
    }

    private LikeCommentRatio fetchLikeCommentRatio(MapSqlParameterSource params) {
        String sql = """
                SELECT 
                    COALESCE(SUM(like_count), 0) AS total_likes,
                    COALESCE(SUM(comment_count), 0) AS total_comments,
                    CASE 
                        WHEN COALESCE(SUM(comment_count), 0) = 0 THEN 
                            CASE WHEN COALESCE(SUM(like_count), 0) = 0 THEN 0.0 ELSE CAST(SUM(like_count) AS REAL) END
                        ELSE ROUND(CAST(SUM(like_count) AS REAL) / CAST(SUM(comment_count) AS REAL), 2)
                    END AS ratio
                FROM posts
                WHERE author_id = :userId
                """;

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new LikeCommentRatio(
                rs.getLong("total_likes"),
                rs.getLong("total_comments"),
                rs.getDouble("ratio")
        ));
    }

    private MediaLikesStats fetchMediaLikesStats(MapSqlParameterSource params) {
        String sql = """
                SELECT
                    CASE 
                        WHEN LOWER(image_url) LIKE '%.mp4' 
                          OR LOWER(image_url) LIKE '%.mov' 
                          OR LOWER(image_url) LIKE '%.webm' 
                          OR LOWER(image_url) LIKE '%.avi' 
                          OR LOWER(image_url) LIKE '%.mkv' 
                        THEN 'VIDEO'
                        ELSE 'PHOTO'
                    END AS media_type,
                    COUNT(id) AS post_count,
                    COALESCE(AVG(like_count), 0.0) AS avg_likes
                FROM posts
                WHERE author_id = :userId
                GROUP BY media_type
                """;

        double photoAvgLikes = 0.0;
        long photoPostCount = 0;
        double videoAvgLikes = 0.0;
        long videoPostCount = 0;

        List<MediaRow> rows = jdbcTemplate.query(sql, params, (rs, rowNum) -> new MediaRow(
                rs.getString("media_type"),
                rs.getLong("post_count"),
                rs.getDouble("avg_likes")
        ));

        for (MediaRow row : rows) {
            if ("VIDEO".equalsIgnoreCase(row.mediaType())) {
                videoPostCount = row.postCount();
                videoAvgLikes = Math.round(row.avgLikes() * 100.0) / 100.0;
            } else {
                photoPostCount = row.postCount();
                photoAvgLikes = Math.round(row.avgLikes() * 100.0) / 100.0;
            }
        }

        return new MediaLikesStats(photoAvgLikes, photoPostCount, videoAvgLikes, videoPostCount);
    }

    private EngagementRanking fetchEngagementRanking(MapSqlParameterSource params) {
        String sql = """
                WITH user_avg AS (
                    SELECT 
                        author_id,
                        AVG(like_count + comment_count) AS avg_eng
                    FROM posts
                    GROUP BY author_id
                ),
                cur_user AS (
                    SELECT COALESCE((SELECT avg_eng FROM user_avg WHERE author_id = :userId), 0.0) AS cur_eng
                )
                SELECT
                    (SELECT cur_eng FROM cur_user) AS user_avg_eng,
                    (SELECT COUNT(*) FROM user_avg WHERE avg_eng > (SELECT cur_eng FROM cur_user)) + 1 AS user_rank,
                    (SELECT COUNT(*) FROM user_avg) AS total_users_with_posts,
                    (SELECT COUNT(*) FROM users) AS total_users
                """;

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> {
            double userAvgEng = rs.getDouble("user_avg_eng");
            long rank = rs.getLong("user_rank");
            long totalUsers = rs.getLong("total_users");
            long totalUsersWithPosts = rs.getLong("total_users_with_posts");

            long baseTotal = totalUsers > 0 ? totalUsers : totalUsersWithPosts;
            double topPercentile = 100.0;
            if (baseTotal > 0) {
                topPercentile = ((double) rank / baseTotal) * 100.0;
            }

            return new EngagementRanking(
                    Math.round(userAvgEng * 100.0) / 100.0,
                    rank,
                    baseTotal,
                    Math.round(topPercentile * 100.0) / 100.0
            );
        });
    }

    private record MediaRow(String mediaType, long postCount, double avgLikes) {}
}
