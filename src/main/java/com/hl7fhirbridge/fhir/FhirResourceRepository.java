package com.hl7fhirbridge.fhir;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class FhirResourceRepository {

    private static final String COLUMNS =
            "id, resource_type, resource_id, version, content::text as content, created_at, source_message_id";

    private final JdbcClient jdbcClient;

    public FhirResourceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public FhirResource insert(FhirResource resource) {
        return jdbcClient.sql("""
                        INSERT INTO fhir_resource (resource_type, resource_id, version, content, source_message_id)
                        VALUES (:resourceType, :resourceId, :version, :content::jsonb, :sourceMessageId)
                        RETURNING %s
                        """.formatted(COLUMNS))
                .param("resourceType", resource.resourceType())
                .param("resourceId", resource.resourceId())
                .param("version", resource.version())
                .param("content", resource.content())
                .param("sourceMessageId", resource.sourceMessageId())
                .query(FhirResource.class)
                .single();
    }

    public Optional<FhirResource> findBySourceMessageId(long sourceMessageId) {
        return jdbcClient.sql("SELECT %s FROM fhir_resource WHERE source_message_id = :sourceMessageId".formatted(COLUMNS))
                .param("sourceMessageId", sourceMessageId)
                .query(FhirResource.class)
                .optional();
    }
}
