package com.instakill.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${media.base-path:media}")
    private String mediaBasePath;

    private Cookie authCookie;

    @BeforeEach
    void setupUser() throws Exception {
        String username = "mediauser_" + System.currentTimeMillis();
        String registerPayload = "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"password123\"}";
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isOk())
                .andReturn();

        authCookie = registerResult.getResponse().getCookie("ACCESS_TOKEN");
        assertThat(authCookie).isNotNull();
    }

    @Test
    void uploadImageSuccessAndStaticServing() throws Exception {
        byte[] fileBytes = "fake-png-content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", fileBytes);

        MvcResult result = mockMvc.perform(multipart("/api/media")
                        .file(file)
                        .cookie(authCookie))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String mediaUrl = json.get("mediaUrl").asText();
        assertThat(mediaUrl).startsWith("/media/").endsWith(".png");

        String filename = mediaUrl.substring("/media/".length());
        Path filePath = Paths.get(mediaBasePath).resolve(filename).toAbsolutePath().normalize();
        assertThat(Files.exists(filePath)).isTrue();
        assertThat(Files.readAllBytes(filePath)).isEqualTo(fileBytes);

        // Test static serving through Spring
        MvcResult getResult = mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(getResult.getResponse().getContentAsByteArray()).isEqualTo(fileBytes);
    }

    @Test
    void uploadVideoSuccessAndStaticServing() throws Exception {
        byte[] videoBytes = new byte[]{0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70}; // mp4 header bytes
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", videoBytes);

        MvcResult result = mockMvc.perform(multipart("/api/media")
                        .file(file)
                        .cookie(authCookie))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String mediaUrl = json.get("mediaUrl").asText();
        assertThat(mediaUrl).startsWith("/media/").endsWith(".mp4");

        // Test static serving of the video file
        MvcResult getResult = mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(getResult.getResponse().getContentAsByteArray()).isEqualTo(videoBytes);
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "pic.jpg", "image/jpeg", "image-content".getBytes());

        mockMvc.perform(multipart("/api/media")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadEmptyFileFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/media")
                        .file(file)
                        .cookie(authCookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadDisallowedExtensionFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "application/octet-stream", "bad".getBytes());

        mockMvc.perform(multipart("/api/media")
                        .file(file)
                        .cookie(authCookie))
                .andExpect(status().isBadRequest());
    }
}
