package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PostCreatedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PostCreatedNotificationListener.class);

    private final NtfyNotificationService ntfyNotificationService;

    public PostCreatedNotificationListener(NtfyNotificationService ntfyNotificationService) {
        this.ntfyNotificationService = ntfyNotificationService;
    }

    @Async
    @EventListener
    public void onPostCreated(PostCreatedEvent event) {
        log.debug("Received PostCreatedEvent asynchronously for post: {}", event.postId());
        ntfyNotificationService.sendPostCreationNotification(event);
    }
}
