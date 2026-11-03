package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NtfyNotificationServiceTest {

    @Test
    void sendsFormattedNotificationToNtfyEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String ntfyUrl = "https://ntfy.sh";
        String topic = "test-topic";
        NtfyNotificationService service = new NtfyNotificationService(builder, ntfyUrl, topic);

        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant now = Instant.now();
        PostCreatedEvent event = new PostCreatedEvent(postId, authorId, "alice", now);

        server.expect(requestTo("https://ntfy.sh/test-topic"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Title", "New Post by alice"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        service.sendPostCreationNotification(event);

        server.verify();
    }
}
