package com.blog.api.config;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldMapPlain401ToUnauthorized() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ResultCode.UNAUTHORIZED));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldMap404xxToNotFound() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ResultCode.USER_NOT_FOUND));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ResultCode.USER_NOT_FOUND.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldMap420xxToBadRequest() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ResultCode.VERIFY_CODE_INVALID));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ResultCode.VERIFY_CODE_INVALID.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldFallbackToInternalServerErrorForUnknownCode() {
        int unknownCode = 70001;
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(unknownCode, "unknown error"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(unknownCode, response.getBody().getCode());
    }
}
