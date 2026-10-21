package com.instakill.media.api.rest;

import com.instakill.common.error.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".mp4", ".mov", ".webm", ".avi", ".mkv"
    );

    private final Path mediaDirectory;

    public MediaController(@Value("${media.base-path:media}") String basePath) {
        String cleanPath = basePath.startsWith("file:") ? basePath.substring(5) : basePath;
        this.mediaDirectory = Paths.get(cleanPath).toAbsolutePath().normalize();
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(mediaDirectory)) {
                Files.createDirectories(mediaDirectory);
                log.info("Media directory created at: {}", mediaDirectory);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create media directory: " + mediaDirectory, e);
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaUploadResponse uploadMedia(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported file type. Allowed formats: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        ensureDirectoryExists();

        String generatedFilename = UUID.randomUUID() + extension;
        Path destination = mediaDirectory.resolve(generatedFilename).normalize();

        if (!destination.startsWith(mediaDirectory)) {
            throw new BadRequestException("Invalid file destination");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save media file: {}", generatedFilename, e);
            throw new RuntimeException("Failed to store media file", e);
        }

        return new MediaUploadResponse("/media/" + generatedFilename);
    }

    public record MediaUploadResponse(String mediaUrl) {
    }
}
