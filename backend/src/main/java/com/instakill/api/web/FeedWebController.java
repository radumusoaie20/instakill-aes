package com.instakill.api.web;

import com.instakill.common.mapping.PostMapper;
import com.instakill.feed.domain.FeedSort;
import com.instakill.infrastructure.security.JwtUserPrincipal;
import com.instakill.interaction.application.InteractionService;
import com.instakill.post.application.PostService;
import com.instakill.user.application.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class FeedWebController {

    private final PostService postService;
    private final UserService userService;
    private final InteractionService interactionService;

    public FeedWebController(PostService postService, UserService userService, InteractionService interactionService) {
        this.postService = postService;
        this.userService = userService;
        this.interactionService = interactionService;
    }

    @GetMapping("/feed")
    public String feed(@AuthenticationPrincipal JwtUserPrincipal principal,
                       @RequestParam(defaultValue = "new") String sort,
                       Model model) {
        FeedSort feedSort = FeedSort.from(sort);
        List<com.instakill.post.domain.Post> postList = postService.listFeed(feedSort, 0, 20);

        List<com.instakill.post.api.rest.PostResponse> posts;
        if (postList.isEmpty()) {
            posts = Collections.emptyList();
        } else {
            Set<UUID> authorIds = postList.stream().map(com.instakill.post.domain.Post::authorId).collect(Collectors.toSet());
            Map<UUID, com.instakill.user.domain.User> authorMap = userService.getUsersByIds(authorIds);
            Set<UUID> likedPostIds = principal != null
                    ? interactionService.getLikedPostIds(principal.id(), postList.stream().map(com.instakill.post.domain.Post::id).toList())
                    : Collections.emptySet();

            posts = postList.stream()
                    .map(post -> PostMapper.toResponse(
                            post,
                            authorMap.get(post.authorId()),
                            likedPostIds.contains(post.id())
                    ))
                    .toList();
        }

        model.addAttribute("posts", posts);
        model.addAttribute("sort", feedSort.name().toLowerCase());
        return "feed";
    }
}
