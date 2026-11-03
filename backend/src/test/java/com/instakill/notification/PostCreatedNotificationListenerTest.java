package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

class PostCreatedNotificationListenerTest {

    @Test
    void onPostCreatedDelegatesToNotificationService() {
        NtfyNotificationService notificationService = Mockito.mock(NtfyNotificationService.class);
        PostCreatedNotificationListener listener = new PostCreatedNotificationListener(notificationService);

        PostCreatedEvent event = new PostCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "bob", Instant.now());
        listener.onPostCreated(event);

        verify(notificationService).sendPostCreationNotification(event);
    }
}
