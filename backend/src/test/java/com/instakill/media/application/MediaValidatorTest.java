package com.instakill.media.application;

import com.instakill.common.error.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaValidatorTest {

    private MediaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MediaValidator();
    }

    @Test
    void validateRejectsNullOrEmptyFile() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");

        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> validator.validate(emptyFile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void validateRejectsUnsupportedExtension() {
        MockMultipartFile exeFile = new MockMultipartFile("file", "test.exe", "application/octet-stream", "content".getBytes());
        assertThatThrownBy(() -> validator.validate(exeFile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void validateAcceptsValidImageAndVideoFiles() {
        MockMultipartFile pngFile = new MockMultipartFile("file", "image.png", "image/png", "content".getBytes());
        MockMultipartFile mp4File = new MockMultipartFile("file", "video.mp4", "video/mp4", "content".getBytes());

        validator.validate(pngFile);
        validator.validate(mp4File);
    }

    @Test
    void isImageCorrectlyIdentifiesImageFormats() {
        assertThat(validator.isImage(".jpg")).isTrue();
        assertThat(validator.isImage(".jpeg")).isTrue();
        assertThat(validator.isImage(".png")).isTrue();
        assertThat(validator.isImage(".gif")).isTrue();
        assertThat(validator.isImage(".webp")).isTrue();

        assertThat(validator.isImage(".mp4")).isFalse();
        assertThat(validator.isImage(".mov")).isFalse();
        assertThat(validator.isImage(".txt")).isFalse();
        assertThat(validator.isImage(null)).isFalse();
    }
}
