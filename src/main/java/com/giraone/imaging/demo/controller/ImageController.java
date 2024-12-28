package com.giraone.imaging.demo.controller;

import com.giraone.imaging.FileInfo;
import com.giraone.imaging.FileTypeDetector;
import com.giraone.imaging.FormatNotSupportedException;
import com.giraone.imaging.java2.ProviderJava2D;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.giraone.imaging.FileTypeDetector.FileType.*;

@SuppressWarnings("unused")
@Controller
public class ImageController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageController.class);

    private final ProviderJava2D providerJava2D = new ProviderJava2D();

    @GetMapping("/list-types")
    public ResponseEntity<List<FileTypeDetector.FileType>> listImageTypes() {

        LOGGER.info("/list-types");
        return ResponseEntity.ok(List.of(UNKNOWN, JPEG, PNG, TIFF, GIF, BMP, PGM, DICOM, PDF));
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
}
