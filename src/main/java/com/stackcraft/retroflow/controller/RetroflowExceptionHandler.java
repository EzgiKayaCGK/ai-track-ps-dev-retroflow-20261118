package com.stackcraft.retroflow.controller;

import com.stackcraft.retroflow.controller.ApiModels.ErrorResponse;
import com.stackcraft.retroflow.exception.BusinessRuleViolationException;
import com.stackcraft.retroflow.exception.ResourceNotFoundException;
import com.stackcraft.retroflow.exception.RetroflowException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class RetroflowExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorResponse(exception.getMessage(), HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(BusinessRuleViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorResponse(exception.getMessage(), HttpStatus.CONFLICT));
    }

    @ExceptionHandler(RetroflowException.class)
    public ResponseEntity<ErrorResponse> badRequest(RetroflowException exception) {
        return ResponseEntity.badRequest().body(errorResponse(exception.getMessage(), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> malformedRequest(Exception exception) {
        return ResponseEntity.badRequest().body(errorResponse("The request is invalid.", HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse("Internal server error.", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private ErrorResponse errorResponse(String message, HttpStatus status) {
        return new ErrorResponse(message, status.value(), Instant.now());
    }
}
