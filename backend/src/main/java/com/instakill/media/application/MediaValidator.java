package com.instakill.media.application;

import com.instakill.common.error.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@Component
public class MediaValidator {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp"
    );

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            ".mp4", ".mov", ".webm", ".avi", ".mkv"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = IMAGE_EXTENSIONS.stream()
            .collect(java.util.stream.Collectors.toSet()); // careful of mutability, we will add video extensions in static block
            
    static {
        ALLOWED_EXTENSIONS.addAll(VIDEO_EXTENSIONS);
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported file type. Allowed formats: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
    }

    public String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    public boolean isImage(String extension) {
        if (extension == null) {
            return false;
        }
        return IMAGE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    public Set<String> getAllowedExtensions() {
        return ALLOWED_EXTENSIONS;
    }
}
