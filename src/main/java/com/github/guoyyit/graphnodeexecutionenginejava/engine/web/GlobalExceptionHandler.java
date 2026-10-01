package com.github.guoyyit.graphnodeexecutionenginejava.engine.web;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.def.DefinitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理，把可区分的失败原因返回给调用方。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DefinitionException.class)
    public ResponseEntity<Map<String, Object>> handleDefinitionException(DefinitionException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "definition_rejected",
                        "reason", e.getReason().name(),
                        "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "not_found",
                        "message", e.getMessage()));
    }
}
