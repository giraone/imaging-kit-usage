package com.giraone.imaging.demo.controller;

import com.giraone.imaging.ConversionCommand;
import com.giraone.imaging.FileInfo;
import com.giraone.imaging.FileTypeDetector;
import com.giraone.imaging.FormatNotSupportedException;
import com.giraone.imaging.ImageConversionException;
import com.giraone.imaging.java2.ProviderJava2D;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@SuppressWarnings("unused")
@Controller
public class ImageController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageController.class);

    private final ProviderJava2D providerJava2D = new ProviderJava2D();

    @GetMapping("/list-types")
    public ResponseEntity<List<String>> listImageTypes() {

        LOGGER.info("/list-types");
        return ResponseEntity.ok(FileTypeDetector.FileType.allTypesAsStrings());
    }

    @PutMapping(value = "/detect-size", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Long> detectSize(HttpServletRequest request) throws IOException {

        InputStream in = request.getInputStream();
        byte[] buffer = new byte[4096];
        long total = 0L;
        int r;
        while ((r = in.read(buffer)) >= 0) {
            total += r;
        }
        LOGGER.info("/detect-size {} Bytes", total);
        return ResponseEntity.ok(total);
    }

    @PutMapping(value = "/detect-type", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<String> detectImageType(InputStream in) {

        FileTypeDetector.FileType detectedFileType = FileTypeDetector.getInstance().getFileType(in);
        LOGGER.info("/detect-type {}", detectedFileType);
        return ResponseEntity.ok(detectedFileType.name());
    }

    @PutMapping(value = "/fetch-file-info", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<FileInfo> fetchFileInfo(InputStream in) throws IOException {

        byte[] buffer = new byte[4096];
        long total = 0L;
        int r;
        File file = File.createTempFile("img", "");
        try (FileOutputStream out = new FileOutputStream(file)) {
            while ((r = in.read(buffer)) >= 0) {
                out.write(buffer, 0, r);
                total += r;
            }
        }
        LOGGER.info("/fetch-file-info {} bytes received", total);
        try {
            FileInfo fileInfo = providerJava2D.fetchFileInfo(file);
            LOGGER.info("/fetch-file-info {}", fileInfo.dumpInfo());
            return ResponseEntity.ok(fileInfo);
        } catch (FormatNotSupportedException e) {
            return ResponseEntity.badRequest().build();
        } finally {
            file.delete();
        }
    }

    @PutMapping(value = "/create-thumbnail", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> createThumbnail(
            InputStream in,
            @RequestHeader(value = "Thumbnail-Width", required = false, defaultValue = "200") int width,
            @RequestHeader(value = "Thumbnail-Height", required = false, defaultValue = "200") int height,
            @RequestHeader(value = "Thumbnail-Quality", required = false, defaultValue = "LOSSY_MEDIUM") String qualityStr) throws IOException {

        // Validate width
        if (width <= 0 || width > 10000) {
            LOGGER.warn("/create-thumbnail invalid width: {}", width);
            return ResponseEntity.badRequest().build();
        }
        // Validate height
        if (height <= 0 || height > 10000) {
            LOGGER.warn("/create-thumbnail invalid height: {}", height);
            return ResponseEntity.badRequest().build();
        }

        // Parse and validate quality
        ConversionCommand.CompressionQuality quality;
        try {
            quality = parseQuality(qualityStr);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("/create-thumbnail invalid quality: {}", qualityStr);
            return ResponseEntity.badRequest().build();
        }

        // Read all input into byte array
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];
        int r;
        while ((r = in.read(buffer)) >= 0) {
            out.write(buffer, 0, r);
        }
        final byte[] imageData = out.toByteArray();

        LOGGER.info("/create-thumbnail {} bytes received, width={}, height={}, quality={}", imageData.length, width, height, quality);

        if (imageData.length == 0) {
            return ResponseEntity.badRequest().build();
        }

        File inputFile = null;
        try {
            // Detect file type from byte array to determine output format and proper file extension
            FileTypeDetector.FileType fileType = FileTypeDetector.getInstance().getFileType(imageData);
            String outputFormat = determineOutputFormat(fileType);
            String extension = getFileExtension(fileType);

            // Create temp file with proper extension so ImageOpener can read it
            inputFile = File.createTempFile("img-in-", extension);
            try (FileOutputStream fos = new FileOutputStream(inputFile)) {
                fos.write(imageData);
            }

            // Create thumbnail using convertImage with ConversionCommand
            ConversionCommand command = new ConversionCommand();
            command.setOutputFormat(outputFormat);
            command.setDimension(new Dimension(width, height));
            command.setQuality(quality);

            ByteArrayOutputStream thumbnailOutput = new ByteArrayOutputStream();
            providerJava2D.convertImage(inputFile, thumbnailOutput, command);

            byte[] thumbnailBytes = thumbnailOutput.toByteArray();
            LOGGER.info("/create-thumbnail thumbnail created: {} bytes, format={}", thumbnailBytes.length, outputFormat);

            // Set appropriate content type
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(outputFormat));
            headers.setContentLength(thumbnailBytes.length);

            return new ResponseEntity<>(thumbnailBytes, headers, HttpStatus.OK);

        } catch (FormatNotSupportedException e) {
            LOGGER.error("/create-thumbnail format not supported", e);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        } catch (ImageConversionException e) {
            LOGGER.error("/create-thumbnail conversion failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            if (inputFile != null && inputFile.exists()) {
                inputFile.delete();
            }
        }
    }

    /**
     * Parse quality string from header to CompressionQuality enum.
     * Maps user-friendly names to internal enum values.
     */
    private ConversionCommand.CompressionQuality parseQuality(String qualityStr) {
        return switch (qualityStr.toUpperCase()) {
            case "LOSSY_LOW", "LOSSY_SPEED" -> ConversionCommand.CompressionQuality.LOSSY_SPEED;
            case "LOSSY_MEDIUM" -> ConversionCommand.CompressionQuality.LOSSY_MEDIUM;
            case "LOSSY_HIGH", "LOSSY_BEST" -> ConversionCommand.CompressionQuality.LOSSY_BEST;
            case "LOSSLESS" -> ConversionCommand.CompressionQuality.LOSSLESS;
            default -> throw new IllegalArgumentException("Invalid quality: " + qualityStr);
        };
    }

    /**
     * Determine output format (MIME type) based on input file type.
     * Defaults to JPEG for most image formats, keeps PNG for PNG input.
     */
    private String determineOutputFormat(FileTypeDetector.FileType fileType) {
        return switch (fileType) {
            case PNG -> "image/png";
            case GIF -> "image/gif";
            default -> "image/jpeg";
        };
    }

    /**
     * Get file extension (with dot) based on file type.
     */
    private String getFileExtension(FileTypeDetector.FileType fileType) {
        return switch (fileType) {
            case JPEG -> ".jpg";
            case PNG -> ".png";
            case GIF -> ".gif";
            case BMP -> ".bmp";
            case TIFF -> ".tif";
            case PGM -> ".pgm";
            case PDF -> ".pdf";
            default -> ".img";
        };
    }
}
