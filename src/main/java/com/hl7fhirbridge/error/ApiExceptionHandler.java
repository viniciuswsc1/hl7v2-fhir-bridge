package com.hl7fhirbridge.error;

import com.hl7fhirbridge.ingestion.UnparseableMessageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Handlers here always return a fixed, human-written message, never the exception's own
// message - HAPI's parser errors can quote PID/OBX fragments back, which would leak
// patient data into an error response.
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnparseableMessageException.class)
    public ResponseEntity<ErrorResponse> handleUnparseable(UnparseableMessageException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    public record ErrorResponse(String error) {
    }
}
