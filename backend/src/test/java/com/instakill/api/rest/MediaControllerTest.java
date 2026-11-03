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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    private byte[] createTestImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.GREEN);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void uploadImageSuccessAndStaticServing() throws Exception {
        byte[] fileBytes = createTestImage(500, 300);
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

        // Check image was processed: scaled down to width 300
        BufferedImage savedImage = ImageIO.read(filePath.toFile());
        assertThat(savedImage).isNotNull();
        assertThat(savedImage.getWidth()).isEqualTo(300);

        // Test static serving through Spring
        MvcResult getResult = mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andReturn();
        byte[] servedBytes = getResult.getResponse().getContentAsByteArray();
        BufferedImage servedImage = ImageIO.read(new ByteArrayInputStream(servedBytes));
        assertThat(servedImage).isNotNull();
        assertThat(servedImage.getWidth()).isEqualTo(300);
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

        // Test static serving of the video file (preserved as is)
        MvcResult getResult = mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(getResult.getResponse().getContentAsByteArray()).isEqualTo(videoBytes);
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        byte[] fileBytes = createTestImage(100, 100);
        MockMultipartFile file = new MockMultipartFile("file", "pic.jpg", "image/jpeg", fileBytes);

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
