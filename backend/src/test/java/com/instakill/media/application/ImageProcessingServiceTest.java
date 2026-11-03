package com.instakill.media.application;

import com.instakill.common.error.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessingServiceTest {

    private ImageProcessingService service;

    @BeforeEach
    void setUp() {
        service = new ImageProcessingService();
    }

    private byte[] createTestImage(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    @Test
    void scalesLargeImageToMaxWidth300WhilePreservingAspectRatio() throws Exception {
        // Original 600x400 (aspect ratio 1.5)
        byte[] inputBytes = createTestImage(600, 400, "png");

        byte[] processedBytes = service.processImage(new ByteArrayInputStream(inputBytes), ".png");
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(processedBytes));

        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isEqualTo(300);
        assertThat(result.getHeight()).isEqualTo(200);
    }

    @Test
    void doesNotUpscaleImageSmallerThan300px() throws Exception {
        // Original 200x150
        byte[] inputBytes = createTestImage(200, 150, "png");

        byte[] processedBytes = service.processImage(new ByteArrayInputStream(inputBytes), ".png");
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(processedBytes));

        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isEqualTo(200);
        assertThat(result.getHeight()).isEqualTo(150);
    }

    @Test
    void addsWatermarkAtBottomRight() throws Exception {
        // Create solid black image
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, 300, 300);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);

        byte[] processedBytes = service.processImage(new ByteArrayInputStream(baos.toByteArray()), ".png");
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(processedBytes));

        assertThat(result).isNotNull();
        // Since watermark is drawn in bottom-right with white/shadow pixels,
        // the bottom-right region should no longer be completely solid black
        boolean foundNonBlackPixel = false;
        for (int x = 200; x < 300; x++) {
            for (int y = 250; y < 300; y++) {
                int rgb = result.getRGB(x, y) & 0x00FFFFFF;
                if (rgb > 0) {
                    foundNonBlackPixel = true;
                    break;
                }
            }
        }
        assertThat(foundNonBlackPixel).isTrue();
    }

    @Test
    void throwsBadRequestExceptionOnCorruptImage() {
        byte[] invalidBytes = "not-an-image".getBytes();

        assertThatThrownBy(() -> service.processImage(new ByteArrayInputStream(invalidBytes), ".png"))
                .isInstanceOf(BadRequestException.class);
    }
}
