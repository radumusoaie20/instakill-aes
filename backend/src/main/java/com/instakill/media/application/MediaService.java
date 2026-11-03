package com.instakill.media.application;

import com.instakill.common.error.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaValidator mediaValidator;
    private final ImageProcessingService imageProcessingService;
    private final Path mediaDirectory;

    public MediaService(
            MediaValidator mediaValidator,
            ImageProcessingService imageProcessingService,
            @Value("${media.base-path:media}") String basePath
    ) {
        this.mediaValidator = mediaValidator;
        this.imageProcessingService = imageProcessingService;
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

    public String saveMedia(MultipartFile file) {
        mediaValidator.validate(file);

        String extension = mediaValidator.extractExtension(file.getOriginalFilename());
        ensureDirectoryExists();

        String generatedFilename = UUID.randomUUID() + extension;
        Path destination = mediaDirectory.resolve(generatedFilename).normalize();

        if (!destination.startsWith(mediaDirectory)) {
            throw new BadRequestException("Invalid file destination");
        }

        try {
            if (mediaValidator.isImage(extension)) {
                try (InputStream inputStream = file.getInputStream()) {
                    byte[] processedBytes = imageProcessingService.processImage(inputStream, extension);
                    Files.write(destination, processedBytes);
                }
            } else {
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            log.error("Failed to save media file: {}", generatedFilename, e);
            throw new RuntimeException("Failed to store media file", e);
        }

        return "/media/" + generatedFilename;
    }
}
