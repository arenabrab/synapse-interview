package com.synapseinterview.controller;

import com.synapseinterview.model.RoutingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RoutingResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(RoutingResponse.failure("Request body is missing or malformed."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RoutingResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.debug("Unsupported media type: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(RoutingResponse.failure("Content-Type must be application/json."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RoutingResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error processing request", ex);
        return ResponseEntity.internalServerError()
                .body(RoutingResponse.failure("An unexpected error occurred."));
    }
}
