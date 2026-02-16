package com.giraone.imaging.demo.controller;

import com.giraone.imaging.ConversionCommand;
import com.giraone.imaging.FileInfo;
import com.giraone.imaging.FileTypeDetector;
import com.giraone.imaging.FormatNotSupportedException;
import com.giraone.imaging.ImagingProvider;
import com.giraone.imaging.ThumbnailProvider;
import com.giraone.imaging.pdf.PdfProvider;
import com.giraone.imaging.text.MarkdownProvider;
import com.giraone.imaging.video.VideoProvider;
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
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static com.giraone.imaging.ConversionCommand.*;
import static com.giraone.imaging.ConversionCommand.CompressionQuality.LOSSLESS;

@SuppressWarnings("unused")
@Controller
public class ImageController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageController.class);

    private final ImagingProvider imagingProvider = ImagingProvider.getInstance();
    private final PdfProvider pdfProvider = PdfProvider.getInstance();
    private final MarkdownProvider markdownProvider = MarkdownProvider.getInstance();
    private final VideoProvider videoProvider = VideoProvider.getInstance();

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
            final FileInfo fileInfo = imagingProvider.fetchFileInfo(file);
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
        final byte[] inputFileData = out.toByteArray();

        LOGGER.info("/create-thumbnail {} bytes received, width={}, height={}, quality={}", inputFileData.length, width, height, quality);

        if (inputFileData.length == 0) {
            return ResponseEntity.badRequest().build();
        }

        File inputFile = null;
        try {
            // Detect file type from byte array to determine output format and proper file extension
            FileTypeDetector.FileType fileType = FileTypeDetector.getInstance().getFileType(inputFileData);
            String inputFormat = determineOutputFormat(fileType);
            String extension = getFileExtension(fileType);
            ThumbnailProvider thumbnailProvider = determineThumbnailProvider(inputFormat);

            // Create temp file with proper extension so ImageOpener can read it
            inputFile = File.createTempFile("file-in-", extension);
            Files.write(inputFile.toPath(), inputFileData, StandardOpenOption.TRUNCATE_EXISTING);
            File outputFile = File.createTempFile("thumb-out-", extension);
            String outputFormat = quality == LOSSLESS ? MIME_TYPE_PNG : MIME_TYPE_JPEG;

            // Create thumbnail using createThumbnail with ConversionCommand
            ConversionCommand command = new ConversionCommand();
            command.setOutputFile(outputFile);
            command.setOutputFormat(outputFormat);
            command.setDimension(new Dimension(width, height));
            command.setQuality(quality);
            thumbnailProvider.createThumbnail(inputFile, command);

            long outputByteSize = outputFile.length();
            LOGGER.info("/create-thumbnail inputFormat={} outputFormat={} outputByteSize={}", inputFormat, outputFormat, outputByteSize);

            // Set appropriate content type
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(outputFormat));
            headers.setContentLength(outputByteSize);

            // return the complete file
            return new ResponseEntity<>(Files.readAllBytes(outputFile.toPath()), headers, HttpStatus.OK);

        } catch (FormatNotSupportedException e) {
            LOGGER.error("/create-thumbnail format not supported", e);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        } catch (Exception e) {
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
            case "LOSSLESS" -> LOSSLESS;
            default -> throw new IllegalArgumentException("Invalid quality: " + qualityStr);
        };
    }

    private ThumbnailProvider determineThumbnailProvider(String format) {
        if ("text/markdown".equals(format)) {
            return markdownProvider;
        } else if ("application/pdf".equals(format)) {
            return pdfProvider;
        } else if ("video/mp4".equals(format)) {
            return videoProvider;
        } else {
            return imagingProvider;
        }
    }

    /**
     * Determine output format (MIME type) based on input file type.
     * Defaults to JPEG for most image formats, keeps PNG for PNG input.
     */
    private String determineOutputFormat(FileTypeDetector.FileType fileType) {
        return switch (fileType) {
            case JPEG -> MIME_TYPE_JPEG;
            case PNG -> MIME_TYPE_PNG;
            case GIF -> MIME_TYPE_GIF;
            case PDF -> MIME_TYPE_PDF;
            case MARKDOWN -> MIME_TYPE_MARKDOWN;
            case MP4 -> MIME_TYPE_MP4;
            default -> MIME_TYPE_UNKNOWN;
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
