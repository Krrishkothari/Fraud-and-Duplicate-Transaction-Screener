package com.txnscreen.ingest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

import com.txnscreen.api.ApiException;

final class IngestAssertions {

    private IngestAssertions() {
    }

    static InputStream utf8(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    static ApiException assertApiError(String code, HttpStatus status, Executable call) {
        ApiException error = assertThrows(ApiException.class, call);
        assertAll(
                () -> assertEquals(code, error.getCode()),
                () -> assertEquals(status, error.getStatus()));
        return error;
    }
}
