package com.hl7fhirbridge.ingestion;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import com.hl7fhirbridge.hl7.Hl7Parser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final Hl7Parser hl7Parser;
    private final RawMessageRepository rawMessageRepository;

    public MessageController(Hl7Parser hl7Parser, RawMessageRepository rawMessageRepository) {
        this.hl7Parser = hl7Parser;
        this.rawMessageRepository = rawMessageRepository;
    }

    @PostMapping(consumes = "text/plain")
    public ResponseEntity<IngestionResult> ingest(@RequestBody String rawPayload) {
        MshHeader header = readHeader(rawPayload);
        RawMessage candidate = RawMessage.capture(header.controlId(), header.messageType(), rawPayload);

        try {
            RawMessage stored = rawMessageRepository.insert(candidate);
            return ResponseEntity.status(HttpStatus.CREATED).body(new IngestionResult(stored.id(), header.controlId(), false));
        } catch (DuplicateKeyException alreadyIngested) {
            RawMessage existing = rawMessageRepository.findByControlId(header.controlId())
                    .orElseThrow(() -> alreadyIngested);
            return ResponseEntity.ok(new IngestionResult(existing.id(), header.controlId(), true));
        }
    }

    // MSH-9 (message type ^ trigger event) and MSH-10 (control id) are the minimum a message
    // needs to be tracked and deduplicated. PID/OBX parsing happens later, once the raw
    // message is already durable.
    private MshHeader readHeader(String rawPayload) {
        try {
            Message message = hl7Parser.parse(rawPayload);
            Terser terser = new Terser(message);
            String controlId = terser.get("MSH-10");
            String messageCode = terser.get("MSH-9-1");
            String triggerEvent = terser.get("MSH-9-2");
            if (isBlank(controlId) || isBlank(messageCode) || isBlank(triggerEvent)) {
                throw new UnparseableMessageException("cabecalho MSH incompleto: MSH-9 e MSH-10 sao obrigatorios");
            }
            return new MshHeader(controlId, messageCode + "^" + triggerEvent);
        } catch (HL7Exception e) {
            throw new UnparseableMessageException("mensagem HL7 malformada", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record MshHeader(String controlId, String messageType) {
    }

    public record IngestionResult(Long id, String controlId, boolean duplicate) {
    }
}
