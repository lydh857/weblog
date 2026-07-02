package com.blog.api.config;

import com.blog.api.security.RateLimitPenaltyService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private RateLimitPenaltyService rateLimitPenaltyService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void shouldMapPlain401ToUnauthorized() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ResultCode.UNAUTHORIZED), request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), response.getBody().getCode());
        verify(rateLimitPenaltyService, never()).handleRateLimitHit(request);
    }

    @Test
    void shouldMap404xxToNotFound() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ResultCode.USER_NOT_FOUND), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ResultCode.USER_NOT_FOUND.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldMap420xxToBadRequest() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ResultCode.VERIFY_CODE_INVALID), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ResultCode.VERIFY_CODE_INVALID.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldFallbackToInternalServerErrorForUnknownCode() {
        int unknownCode = 70001;
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(unknownCode, "unknown error"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(unknownCode, response.getBody().getCode());
        verify(rateLimitPenaltyService, never()).handleRateLimitHit(request);
    }

    @Test
    void shouldTriggerPenaltyServiceWhenRateLimited() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ResultCode.RATE_LIMIT), request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ResultCode.RATE_LIMIT.getCode(), response.getBody().getCode());
        verify(rateLimitPenaltyService).handleRateLimitHit(request);
    }
}
