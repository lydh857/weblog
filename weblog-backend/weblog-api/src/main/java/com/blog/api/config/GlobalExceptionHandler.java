package com.blog.api.config;

import com.blog.api.security.RateLimitPenaltyService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.LogMaskUtil;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.NotPermissionException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 * 生产环境不返回详细错误堆栈，只返回友好错误提示
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final RateLimitPenaltyService rateLimitPenaltyService;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        if (e.getCode() == ResultCode.RATE_LIMIT.getCode()) {
            try {
                rateLimitPenaltyService.handleRateLimitHit(request);
            } catch (Exception ex) {
                log.warn("处理限流处罚失败: {}", ex.getMessage());
            }
        }
        HttpStatus status = resolveBusinessHttpStatus(e.getCode());
        String message = LogMaskUtil.mask(e.getMessage());
        log.warn("业务异常: code={}, status={}, message={}", e.getCode(), status.value(), message);
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        return Result.fail(401, "未登录或Token已过期");
    }

    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotRoleException(NotRoleException e) {
        return Result.fail(403, "无权限访问");
    }

    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotPermissionException(NotPermissionException e) {
        return Result.fail(403, "无权限访问");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return Result.fail(400, message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数绑定失败");
        return Result.fail(400, message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("静态资源不存在: {}", e.getResourcePath());
        return Result.fail(404, "资源不存在");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        // 生产环境不暴露堆栈信息
        return Result.fail("系统繁忙，请稍后重试");
    }

    private HttpStatus resolveBusinessHttpStatus(int businessCode) {
        if (businessCode >= 400 && businessCode < 600) {
            HttpStatus direct = HttpStatus.resolve(businessCode);
            if (direct != null) {
                return direct;
            }
        }
        if (businessCode >= 42000 && businessCode < 43000) {
            return HttpStatus.BAD_REQUEST;
        }
        if (businessCode >= 40000 && businessCode < 60000) {
            int statusCode = businessCode / 100;
            HttpStatus mapped = HttpStatus.resolve(statusCode);
            if (mapped != null) {
                return mapped;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
