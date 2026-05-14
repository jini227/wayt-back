package com.wayt.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTests {
    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void maxUploadSizeErrorsReturnBadRequest() {
        var response = handler.handleMultipartException(new MaxUploadSizeExceededException(5 * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("error", "Bad Request");
        assertThat((Map<String, Object>) response.getBody()).containsKey("message");
    }

    @Test
    void malformedMultipartErrorsReturnBadRequest() {
        var response = handler.handleMultipartException(new MultipartException("missing file part"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("error", "Bad Request");
    }
}
