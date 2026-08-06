package com.hl7fhirbridge.ingestion;

public class UnparseableMessageException extends RuntimeException {

    public UnparseableMessageException(String message) {
        super(message);
    }

    public UnparseableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
