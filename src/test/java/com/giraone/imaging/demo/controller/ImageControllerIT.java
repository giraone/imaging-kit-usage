package com.giraone.imaging.demo.controller;

import com.giraone.imaging.FileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

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
@AutoConfigureWebTestClient
class ImageControllerIT {

    @Autowired
    private WebTestClient webTestClient;

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
    void listImageTypes_returns_correct_list() {
        /// arrange
        ParameterizedTypeReference<List<String>> typeRef = new ParameterizedTypeReference<>() {
        };
        /// act
        List<String> result = webTestClient.get().uri("/list-types")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON) // Normally not necessary
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(typeRef)
            .returnResult()
            .getResponseBody();
        /// assert
        assertThat(result)
            .isNotNull()
            .hasSize(9)
            .containsExactlyInAnyOrder(List.of(
                "JPEG", "PNG", "GIF", "BMP", "TIFF", "PDF", "DICOM", "PGM", "UNKNOWN"
            ).toArray(new String[0]));
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
        Long result = webTestClient.put().uri("/detect-size")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(fileContent)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Long.class)
            .returnResult()
            .getResponseBody();
        /// assert
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(fileContent.length);
        assertThat(result).isGreaterThanOrEqualTo(expectedMinSize);
    }

    @Test
    void detectSize_returns_zero_for_empty_content() {
        /// arrange
        byte[] emptyContent = new byte[0];
        /// act
        Long result = webTestClient.put().uri("/detect-size")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(emptyContent)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Long.class)
            .returnResult()
            .getResponseBody();
        /// assert
        assertThat(result).isEqualTo(0L);
    }

    @Test
    void detectSize_handles_large_files() {
        /// arrange
        byte[] largeContent = new byte[1024 * 1024]; // 1 MB
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        /// act
        Long result = webTestClient.put().uri("/detect-size")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(largeContent)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Long.class)
            .returnResult()
            .getResponseBody();
        /// assert
        assertThat(result).isEqualTo(1024L * 1024L);
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
        String result = webTestClient.put().uri("/detect-type")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        /// assert
        assertThat(result).isEqualTo(expectedBody);
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
        FileInfo fileInfo = webTestClient.put().uri("/fetch-file-info")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isOk()
            .expectBody(FileInfo.class)
            .returnResult()
            .getResponseBody();
        /// assert
        assertThat(fileInfo).isNotNull();
        assertThat(fileInfo.getMimeType()).isEqualTo(expectedMimeType);
        assertThat(fileInfo.getWidth()).isEqualTo(expectedWidth);
        assertThat(fileInfo.getHeight()).isEqualTo(expectedHeight);
        assertThat(fileInfo.getBitsPerPixel()).isEqualTo(expectedBitsPerPixel);
    }

    @Test
    void fetchFileInfo_returns_bad_request_for_unsupported_format() throws Exception {
        /// arrange
        byte[] textContent = loadTestFile(TEST_TEXT);
        /// act & assert
        webTestClient.put().uri("/fetch-file-info")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(textContent)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void fetchFileInfo_returns_bad_request_for_empty_content() {
        /// arrange
        byte[] emptyContent = new byte[0];
        /// act & assert
        webTestClient.put().uri("/fetch-file-info")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(emptyContent)
            .exchange()
            .expectStatus().isBadRequest();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // PUT /create-thumbnail tests
    // -----------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "image-01.jpg,100,LOSSY_MEDIUM",
        "image-01.jpg,200,LOSSY_LOW",
        "image-01.jpg,300,LOSSY_HIGH",
        "image-01.jpg,150,LOSSLESS",
        "image-01.png,100,LOSSY_MEDIUM",
        "image-01.gif,100,LOSSY_MEDIUM"
    })
    void createThumbnail_creates_thumbnail_with_correct_width(String testFileName, int width, String quality) throws Exception {
        /// arrange
        byte[] imageContent = loadTestFile(testFileName);
        /// act
        byte[] thumbnailBytes = webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Width", String.valueOf(width))
            .header("Thumbnail-Quality", quality)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(imageContent)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().exists("Content-Type")
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();
        /// assert
        assertThat(thumbnailBytes).isNotNull();
        assertThat(thumbnailBytes.length).isGreaterThan(0);
        assertThat(thumbnailBytes.length).isLessThan(imageContent.length);
    }

    @Test
    void createThumbnail_returns_jpeg_for_jpeg_input() throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act
        webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Width", "100")
            .header("Thumbnail-Quality", "LOSSY_MEDIUM")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.IMAGE_JPEG);
    }

    @Test
    void createThumbnail_returns_png_for_png_input() throws Exception {
        /// arrange
        byte[] pngContent = loadTestFile(TEST_IMAGE_PNG);
        /// act
        webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Width", "100")
            .header("Thumbnail-Quality", "LOSSY_MEDIUM")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(pngContent)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.IMAGE_PNG);
    }

    @Test
    void createThumbnail_uses_default_width_when_not_specified() throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act
        byte[] thumbnailBytes = webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Quality", "LOSSY_MEDIUM")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();
        /// assert
        assertThat(thumbnailBytes).isNotNull();
        assertThat(thumbnailBytes.length).isGreaterThan(0);
    }

    @Test
    void createThumbnail_uses_default_quality_when_not_specified() throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act
        byte[] thumbnailBytes = webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Width", "100")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();
        /// assert
        assertThat(thumbnailBytes).isNotNull();
        assertThat(thumbnailBytes.length).isGreaterThan(0);
    }

    @Test
    void createThumbnail_returns_bad_request_for_invalid_width() throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act & assert - negative width
        webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Width", "-100")
            .header("Thumbnail-Quality", "LOSSY_MEDIUM")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void createThumbnail_returns_bad_request_for_zero_width() throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act & assert
        webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Width", "0")
            .header("Thumbnail-Quality", "LOSSY_MEDIUM")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void createThumbnail_returns_bad_request_for_excessive_width() throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act & assert
        webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Width", "20000")
            .header("Thumbnail-Quality", "LOSSY_MEDIUM")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void createThumbnail_returns_bad_request_for_invalid_quality() throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act & assert
        webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Width", "100")
            .header("Thumbnail-Quality", "INVALID_QUALITY")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void createThumbnail_returns_bad_request_for_empty_content() {
        /// arrange
        byte[] emptyContent = new byte[0];
        /// act & assert
        webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Width", "100")
            .header("Thumbnail-Quality", "LOSSY_MEDIUM")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(emptyContent)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void createThumbnail_returns_unsupported_media_type_for_text_file() throws Exception {
        /// arrange
        byte[] textContent = loadTestFile(TEST_TEXT);
        /// act & assert
        webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Width", "100")
            .header("Thumbnail-Quality", "LOSSY_MEDIUM")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(textContent)
            .exchange()
            .expectStatus().isEqualTo(415); // UNSUPPORTED_MEDIA_TYPE
    }

    @ParameterizedTest
    @CsvSource({
        "LOSSY_LOW",
        "LOSSY_MEDIUM",
        "LOSSY_HIGH",
        "LOSSLESS",
        "LOSSY_SPEED",
        "LOSSY_BEST"
    })
    void createThumbnail_accepts_all_valid_quality_values(String quality) throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act & assert
        webTestClient.put().uri("/create-thumbnail")
            .header("Thumbnail-Width", "100")
            .header("Thumbnail-Quality", quality)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isOk();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Cross-cutting concerns tests
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void all_endpoints_handle_missing_content_type_header() throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act & assert - No Content-Type header
        webTestClient.put().uri("/detect-type")
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void endpoints_accepting_binary_data_handle_various_content_types() throws Exception {
        /// arrange
        byte[] jpegContent = loadTestFile(TEST_IMAGE_JPEG);
        /// act
        String result = webTestClient.put().uri("/detect-type")
            .contentType(MediaType.parseMediaType("image/jpeg"))
            .bodyValue(jpegContent)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        /// assert
        assertThat(result).isEqualTo("JPEG");
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
