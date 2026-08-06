package com.hl7fhirbridge.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

public record RawMessage(
        Long id,
        OffsetDateTime receivedAt,
        String controlId,
        String messageType,
        String payload,
        String sha256
) {

    public static RawMessage capture(String controlId, String messageType, String payload) {
        return new RawMessage(null, null, controlId, messageType, payload, sha256Of(payload));
    }

    private static String sha256Of(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }
}
