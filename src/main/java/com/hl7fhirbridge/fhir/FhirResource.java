package com.hl7fhirbridge.fhir;

import java.time.OffsetDateTime;

public record FhirResource(
        Long id,
        String resourceType,
        String resourceId,
        int version,
        String content,
        OffsetDateTime createdAt,
        Long sourceMessageId
) {

    public static FhirResource capture(String resourceType, String resourceId, String content, long sourceMessageId) {
        return new FhirResource(null, resourceType, resourceId, 1, content, null, sourceMessageId);
    }
}
