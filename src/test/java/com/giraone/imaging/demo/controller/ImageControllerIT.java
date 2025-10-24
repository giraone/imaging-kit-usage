package com.giraone.imaging.demo.controller;

import com.giraone.imaging.FileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ImageController endpoints.
 * Tests all REST API endpoints with real Spring Boot context and HTTP calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImageControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    // Test image files from imaging-kit test resources
    private static final String TEST_IMAGE_JPEG = "image-01.jpg";
    private static final String TEST_IMAGE_PNG = "image-01.png";
    private static final String TEST_IMAGE_GIF = "image-01.gif";
    private static final String TEST_PDF = "document-01-PDF-1.3.pdf";
    private static final String TEST_TEXT = "text.txt";

    // -----------------------------------------------------------------------------------------------------------------
    // GET /list-types tests
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void listImageTypes_returns_ok_status() {
        /// act
        ResponseEntity<List> response = restTemplate.getForEntity("/list-types", List.class);
        /// assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listImageTypes_returns_all_supported_types() {
        /// act
        ResponseEntity<String> response = restTemplate.getForEntity("/list-types", String.class);
        /// assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("JPEG", "PNG", "GIF", "BMP", "TIFF", "PDF", "DICOM", "PGM", "UNKNOWN");
    }

    @Test
    void listImageTypes_returns_json_content_type() {
        /// act
        ResponseEntity<String> response = restTemplate.getForEntity("/list-types", String.class);
        /// assert
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().includes(MediaType.APPLICATION_JSON)).isTrue();
    }

    @Test
    void listImageTypes_returns_exactly_nine_types() {
        /// act
        ResponseEntity<List> response = restTemplate.getForEntity("/list-types", List.class);
        /// assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(9);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // PUT /detect-size tests
    // -----------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("provideTestFiles")
    void detectSize_returns_correct_file_size(String fileName, long expectedMinSize) throws Exception {
        /// arrange
        byte[] fileContent = loadTestFile(fileName);
        /// act
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<byte[]> request = new HttpEntity<>(fileContent, headers);
        ResponseEntity<Long> response = restTemplate.exchange("/detect-size", HttpMethod.PUT, request, Long.class);
        /// assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEqualTo(fileContent.length);
        assertThat(response.getBody()).isGreaterThanOrEqualTo(expectedMinSize);
    }

    @Test
    void detectSize_returns_zero_for_empty_content() {
        /// arrange
        byte[] emptyContent = new byte[0];
        /// act
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<byte[]> request = new HttpEntity<>(emptyContent, headers);
        ResponseEntity<Long> response = restTemplate.exchange("/detect-size", HttpMethod.PUT, request, Long.class);
        /// assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(0L);
    }

    @Test
    void detectSize_handles_large_files() {
        /// arrange
        byte[] largeContent = new byte[1024 * 1024]; // 1 MB
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        /// act
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<byte[]> request = new HttpEntity<>(largeContent, headers);
        ResponseEntity<Long> response = restTemplate.exchange("/detect-size", HttpMethod.PUT, request, Long.class);
        /// assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(1024L * 1024L);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // PUT /detect-type tests
    // -----------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "image-01.jpg,JPEG",
        "image-01.png,PNG",
        "image-01.gif,GIF",
        "document-01-PDF-1.3.pdf,PDF",
        "{TEST_TEXT},UNKNOWN",
    })
    void detectType_detects_correctly(String testFileName, String expectedBody) throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(testFileName);
        /// act
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<byte[]> request = new HttpEntity<>(jpegContent, headers);
        ResponseEntity<String> response = restTemplate.exchange("/detect-type", HttpMethod.PUT, request, String.class);
        /// assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedBody);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // PUT /fetch-file-info tests
    // -----------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "image-01.jpg,image/jpeg,1024,768,24",
        "image-01.png,image/png,800,600,24",
        "image-01.gif,image/gif,100,75,8"
    })
    void fetchFileInfo_returns_image_metadata(String testFileName, String expectedMimeType,
                                              Integer expectedWidth, Integer expectedHeight,
                                              Integer expectedBitsPerPixel) throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(testFileName);
        /// act
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<byte[]> request = new HttpEntity<>(jpegContent, headers);
        ResponseEntity<FileInfo> response = restTemplate.exchange("/fetch-file-info", HttpMethod.PUT, request, FileInfo.class);
        /// assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        FileInfo fileInfo = response.getBody();
        assertThat(fileInfo.getMimeType()).isEqualTo(expectedMimeType);
        assertThat(fileInfo.getWidth()).isEqualTo(expectedWidth);
        assertThat(fileInfo.getHeight()).isEqualTo(expectedHeight);
        assertThat(fileInfo.getBitsPerPixel()).isEqualTo(expectedBitsPerPixel);
    }

    @Test
    void fetchFileInfo_returns_bad_request_for_unsupported_format() throws Exception {
        /// arrange
        byte[] textContent = loadTestFile(TEST_TEXT);
        /// act
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<byte[]> request = new HttpEntity<>(textContent, headers);
        ResponseEntity<FileInfo> response = restTemplate.exchange("/fetch-file-info", HttpMethod.PUT, request, FileInfo.class);
        /// assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void fetchFileInfo_returns_bad_request_for_empty_content() {
        /// arrange
        byte[] emptyContent = new byte[0];
        /// act
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<byte[]> request = new HttpEntity<>(emptyContent, headers);
        ResponseEntity<FileInfo> response = restTemplate.exchange("/fetch-file-info", HttpMethod.PUT, request, FileInfo.class);
        /// assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Cross-cutting concerns tests
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void all_endpoints_handle_missing_content_type_header() throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act - No Content-Type header
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<byte[]> request = new HttpEntity<>(jpegContent, headers);
        ResponseEntity<String> response = restTemplate.exchange("/detect-type", HttpMethod.PUT, request, String.class);
        /// assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void endpoints_accepting_binary_data_handle_various_content_types() throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("image/jpeg"));
        HttpEntity<byte[]> request = new HttpEntity<>(jpegContent, headers);
        ResponseEntity<String> response = restTemplate.exchange("/detect-type", HttpMethod.PUT, request, String.class);
        /// assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("JPEG");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Load test file from imaging-kit test resources (classpath).
     * Falls back to creating synthetic data if file not found.
     */
    private byte[] loadTestFile(String fileName) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                // Fallback: create synthetic test data based on file type
                return createSyntheticTestData(fileName);
            }
            return readAllBytes(is);
        }
    }

    /**
     * Read all bytes from input stream.
     */
    private byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    /**
     * Create synthetic test data when actual test files are not available.
     * Uses minimal valid file headers for each format.
     */
    private byte[] createSyntheticTestData(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            // Minimal JPEG header: FFD8FF (SOI + APP0 marker)
            return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01};
        } else if (fileName.endsWith(".png")) {
            // PNG signature: 89 50 4E 47 0D 0A 1A 0A
            return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        } else if (fileName.endsWith(".pdf")) {
            // PDF header: %PDF-1.4
            return "%PDF-1.4\n".getBytes(StandardCharsets.UTF_8);
        } else if (fileName.endsWith(".txt")) {
            return "This is a text file".getBytes(StandardCharsets.UTF_8);
        } else if (fileName.endsWith(".gif")) {
            // GIF header: GIF89a
            return "GIF89a".getBytes(StandardCharsets.UTF_8);
        }
        // Default: random bytes
        return new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};
    }

    /**
     * Provide test files with expected minimum sizes for parameterized tests.
     */
    private static Stream<Arguments> provideTestFiles() {
        return Stream.of(
            Arguments.of(TEST_IMAGE_JPEG, 1000L),
            Arguments.of(TEST_IMAGE_PNG, 1000L),
            Arguments.of(TEST_IMAGE_GIF, 1000L),
            Arguments.of(TEST_PDF, 500L),
            Arguments.of(TEST_TEXT, 10L)
        );
    }
}
