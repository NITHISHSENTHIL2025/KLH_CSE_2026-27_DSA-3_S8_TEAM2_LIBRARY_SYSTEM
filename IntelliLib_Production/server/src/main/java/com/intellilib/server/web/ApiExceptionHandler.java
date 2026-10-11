package com.intellilib.server.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Map<String,Object>> security(SecurityException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok",false,"error",e.getMessage()));
    }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String,Object>> bad(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("ok",false,"error",e.getMessage()));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,Object>> other(Exception e) {
        return ResponseEntity.status(500).body(Map.of("ok",false,"error","Server error","detail",e.getClass().getSimpleName()));
    }
}
