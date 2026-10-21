package com.instakill.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    private final String mediaBasePath;

    public WebMvcConfig(@Value("${media.base-path:media}") String mediaBasePath) {
        this.mediaBasePath = mediaBasePath;
    }

    @PostConstruct
    public void init() {
        try {
            String cleanPath = mediaBasePath.startsWith("file:") ? mediaBasePath.substring(5) : mediaBasePath;
            Path path = Paths.get(cleanPath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created media directory at: {}", path);
            }
        } catch (IOException e) {
            log.error("Failed to create media directory: {}", mediaBasePath, e);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String cleanPath = mediaBasePath.startsWith("file:") ? mediaBasePath.substring(5) : mediaBasePath;
        Path path = Paths.get(cleanPath).toAbsolutePath().normalize();
        String location = path.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/media/**")
                .addResourceLocations(location);
    }
}
