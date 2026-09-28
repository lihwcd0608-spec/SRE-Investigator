package com.maiya.investigator.boot;

import com.maiya.investigator.boot.auth.ForbiddenException;
import com.maiya.investigator.boot.auth.UnauthorizedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidRequest(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage() == null ? "请求无效" : exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidState(IllegalStateException exception) {
        return Map.of("message", exception.getMessage() == null ? "当前状态不可用" : exception.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> unauthorized(UnauthorizedException exception) {
        return Map.of("message", exception.getMessage() == null ? "未登录" : exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> forbidden(ForbiddenException exception) {
        return Map.of("message", exception.getMessage() == null ? "没有权限" : exception.getMessage());
    }
}
