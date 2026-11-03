package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class NtfyNotificationService {

    private static final Logger log = LoggerFactory.getLogger(NtfyNotificationService.class);

    private final RestClient restClient;
    private final String ntfyUrl;
    private final String topic;

    public NtfyNotificationService(
            RestClient.Builder restClientBuilder,
            @Value("${instakill.notifications.ntfy.url:https://ntfy.sh}") String ntfyUrl,
            @Value("${instakill.notifications.ntfy.topic:instakill-demo-posts}") String topic
    ) {
        this.restClient = restClientBuilder.build();
        this.ntfyUrl = ntfyUrl;
        this.topic = topic;
    }

    public void sendPostCreationNotification(PostCreatedEvent event) {
        try {
            String targetUrl = ntfyUrl.endsWith("/") ? ntfyUrl + topic : ntfyUrl + "/" + topic;
            String message = String.format("User '%s' created post %s at %s",
                    event.username(), event.postId(), event.createdAt());

            log.info("Sending post creation notification to {} for post ID: {}", targetUrl, event.postId());

            restClient.post()
                    .uri(targetUrl)
                    .header("Title", "New Post by " + event.username())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Notification successfully sent for post ID: {}", event.postId());
        } catch (Exception e) {
            log.error("Failed to send notification to ntfy for post ID: {}", event.postId(), e);
        }
    }
}
