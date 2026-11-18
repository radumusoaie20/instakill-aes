package com.instakill.post.api.rest;

import com.instakill.common.mapping.CommentMapper;
import com.instakill.common.mapping.PostMapper;
import com.instakill.feed.domain.FeedSort;
import com.instakill.infrastructure.security.JwtUserPrincipal;
import com.instakill.interaction.api.rest.CommentRequest;
import com.instakill.interaction.api.rest.CommentResponse;
import com.instakill.interaction.api.rest.ToggleLikeResponse;
import com.instakill.interaction.application.InteractionService;
import com.instakill.post.application.PostService;
import com.instakill.post.domain.Post;
import com.instakill.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class PostRestController {

    private final PostService postService;
    private final UserService userService;
    private final InteractionService interactionService;

    public PostRestController(PostService postService, UserService userService, InteractionService interactionService) {
        this.postService = postService;
        this.userService = userService;
        this.interactionService = interactionService;
    }

    @PostMapping("/posts")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public PostResponse createPost(@AuthenticationPrincipal JwtUserPrincipal principal,
                                   @Valid @RequestBody CreatePostRequest request) {
        Post post = postService.createPost(principal.id(), request.content(), request.imageUrl());
        return PostMapper.toResponse(post, userService.getById(post.authorId()), false);
    }

    @GetMapping("/posts/{id}")
    public PostResponse getPost(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable UUID id) {
        Post post = postService.getPost(id);
        boolean liked = principal != null && interactionService.hasUserLiked(id, principal.id());
        return PostMapper.toResponse(post, userService.getById(post.authorId()), liked);
    }

    @DeleteMapping("/posts/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deletePost(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable UUID id) {
        postService.deleteOwnPost(id, principal.id());
    }

    @GetMapping("/feed")
    public List<PostResponse> listFeed(@AuthenticationPrincipal JwtUserPrincipal principal,
                                       @RequestParam(defaultValue = "new") String sort,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        FeedSort feedSort = FeedSort.from(sort);
        List<Post> postList = postService.listFeed(feedSort, page, size);
        if (postList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> authorIds = postList.stream().map(Post::authorId).collect(Collectors.toSet());
        Map<UUID, com.instakill.user.domain.User> authorMap = userService.getUsersByIds(authorIds);
        Set<UUID> likedPostIds = principal != null
                ? interactionService.getLikedPostIds(principal.id(), postList.stream().map(Post::id).toList())
                : Collections.emptySet();

        return postList.stream()
                .map(post -> PostMapper.toResponse(
                        post,
                        authorMap.get(post.authorId()),
                        likedPostIds.contains(post.id())
                ))
                .toList();
    }

    @PostMapping("/posts/{id}/comments")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public CommentResponse addComment(@AuthenticationPrincipal JwtUserPrincipal principal,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody CommentRequest request) {
        var comment = interactionService.addComment(id, principal.id(), request.content());
        return CommentMapper.toResponse(comment, userService.getById(comment.authorId()));
    }

    @GetMapping("/posts/{id}/comments")
    public List<CommentResponse> listComments(@PathVariable UUID id,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        var comments = interactionService.listComments(id, page, size);
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> authorIds = comments.stream().map(com.instakill.interaction.domain.Comment::authorId).collect(Collectors.toSet());
        Map<UUID, com.instakill.user.domain.User> authorMap = userService.getUsersByIds(authorIds);

        return comments.stream()
                .map(comment -> CommentMapper.toResponse(comment, authorMap.get(comment.authorId())))
                .toList();
    }

    @PostMapping("/posts/{id}/likes/toggle")
    public ToggleLikeResponse toggleLike(@AuthenticationPrincipal JwtUserPrincipal principal,
                                         @PathVariable UUID id) {
        InteractionService.ToggleLikeResult result = interactionService.toggleLike(id, principal.id());
        return new ToggleLikeResponse(result.liked(), result.likeCount());
    }
}
