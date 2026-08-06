package com.hl7fhirbridge.ingestion;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class RawMessageRepository {

    private static final String COLUMNS = "id, received_at, control_id, message_type, payload, sha256";

    private final JdbcClient jdbcClient;

    public RawMessageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public RawMessage insert(RawMessage message) {
        return jdbcClient.sql("""
                        INSERT INTO raw_message (control_id, message_type, payload, sha256)
                        VALUES (:controlId, :messageType, :payload, :sha256)
                        RETURNING %s
                        """.formatted(COLUMNS))
                .param("controlId", message.controlId())
                .param("messageType", message.messageType())
                .param("payload", message.payload())
                .param("sha256", message.sha256())
                .query(RawMessage.class)
                .single();
    }

    public Optional<RawMessage> findByControlId(String controlId) {
        return jdbcClient.sql("SELECT %s FROM raw_message WHERE control_id = :controlId".formatted(COLUMNS))
                .param("controlId", controlId)
                .query(RawMessage.class)
                .optional();
    }
}
