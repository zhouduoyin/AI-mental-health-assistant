package com.example.rag.web;

import com.example.rag.support.AiInvocationException;
import com.example.rag.support.ForbiddenException;
import com.example.rag.support.ResourceNotFoundException;
import com.example.rag.support.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 统一异常 → RFC 9457 ProblemDetail 响应。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "资源不存在", ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "未登录", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, "无权访问", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "请求参数不合法", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "参数校验失败", detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "文件过大", "请上传小于 20MB 的文件");
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ProblemDetail handleRedisUnavailable(RedisConnectionFailureException ex) {
        log.warn("Redis 连接失败", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "登录服务暂不可用",
                "无法连接 Redis，请确认 Redis 已启动（默认 localhost:6379）");
    }

    @ExceptionHandler(AiInvocationException.class)
    public ProblemDetail handleAiError(AiInvocationException ex) {
        return problem(HttpStatus.BAD_GATEWAY, "AI 服务调用失败", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("未处理异常", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "服务异常", "服务器内部错误，请查看日志");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
