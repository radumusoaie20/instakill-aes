package com.instakill.media.application;

import com.instakill.common.error.BadRequestException;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Service
public class ImageProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessingService.class);
    private static final int MAX_WIDTH = 300;
    private static final String DEFAULT_WATERMARK_TEXT = "Instakill Demo";

    public byte[] processImage(InputStream inputStream, String extension) {
        try {
            BufferedImage originalImage = ImageIO.read(inputStream);
            if (originalImage == null) {
                throw new BadRequestException("Uploaded file is not a valid image");
            }

            // 1. Scale down to maximum width of 300px while preserving aspect ratio
            BufferedImage scaledImage;
            if (originalImage.getWidth() > MAX_WIDTH) {
                scaledImage = Thumbnails.of(originalImage)
                        .width(MAX_WIDTH)
                        .asBufferedImage();
            } else {
                scaledImage = originalImage;
            }

            // 2. Add watermark at bottom-right corner
            BufferedImage watermarkedImage = applyWatermark(scaledImage, DEFAULT_WATERMARK_TEXT);

            // 3. Export to target format
            String format = resolveFormat(extension);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            Thumbnails.of(watermarkedImage)
                    .scale(1.0)
                    .outputFormat(format)
                    .toOutputStream(outputStream);

            return outputStream.toByteArray();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process image with extension {}", extension, e);
            throw new BadRequestException("Failed to process uploaded image: " + e.getMessage());
        }
    }

    private BufferedImage applyWatermark(BufferedImage source, String watermarkText) {
        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage result = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(source, 0, 0, null);

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int fontSize = Math.max(10, Math.min(16, width / 20));
        g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g2d.getFontMetrics();

        int textWidth = fm.stringWidth(watermarkText);
        int textHeight = fm.getHeight();

        int padding = Math.max(6, width / 30);
        int x = width - textWidth - padding;
        int y = height - padding;

        // Draw shadow for contrast
        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.drawString(watermarkText, x + 1, y + 1);

        // Draw watermark text in white
        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.drawString(watermarkText, x, y);

        g2d.dispose();
        return result;
    }

    private String resolveFormat(String extension) {
        if (extension == null) {
            return "png";
        }
        String clean = extension.startsWith(".") ? extension.substring(1).toLowerCase() : extension.toLowerCase();
        if ("jpg".equals(clean) || "jpeg".equals(clean)) {
            return "jpg";
        }
        if ("gif".equals(clean)) {
            return "gif";
        }
        if ("png".equals(clean)) {
            return "png";
        }
        if (ImageIO.getImageWritersByFormatName(clean).hasNext()) {
            return clean;
        }
        return "png";
    }
}
