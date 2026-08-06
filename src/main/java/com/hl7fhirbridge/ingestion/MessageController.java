package com.hl7fhirbridge.ingestion;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import com.hl7fhirbridge.fhir.FhirResource;
import com.hl7fhirbridge.fhir.FhirResourceRepository;
import com.hl7fhirbridge.fhir.ObservationIngestionService;
import com.hl7fhirbridge.hl7.Hl7Parser;
import com.hl7fhirbridge.hl7.OruR01Extractor;
import com.hl7fhirbridge.hl7.ParsedOru;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final Hl7Parser hl7Parser;
    private final OruR01Extractor oruR01Extractor;
    private final RawMessageRepository rawMessageRepository;
    private final FhirResourceRepository fhirResourceRepository;
    private final ObservationIngestionService observationIngestionService;

    public MessageController(Hl7Parser hl7Parser, OruR01Extractor oruR01Extractor,
                              RawMessageRepository rawMessageRepository, FhirResourceRepository fhirResourceRepository,
                              ObservationIngestionService observationIngestionService) {
        this.hl7Parser = hl7Parser;
        this.oruR01Extractor = oruR01Extractor;
        this.rawMessageRepository = rawMessageRepository;
        this.fhirResourceRepository = fhirResourceRepository;
        this.observationIngestionService = observationIngestionService;
    }

    @PostMapping(consumes = "text/plain")
    public ResponseEntity<IngestionResult> ingest(@RequestBody String rawPayload) {
        Message message = parseMessage(rawPayload);
        MshHeader header = readHeader(message);
        RawMessage candidate = RawMessage.capture(header.controlId(), header.messageType(), rawPayload);

        // Committed on its own, before PID/OBX are touched. If mapping throws below,
        // this row stays; only the observation insert rolls back.
        RawMessage stored = persistRaw(candidate, header.controlId());

        // Same lookup covers a real resend and a raw message whose mapping failed last
        // time (no OBX row for it yet) - either way, try to find the Observation first.
        Optional<FhirResource> existingResource = fhirResourceRepository.findBySourceMessageId(stored.id());
        if (existingResource.isPresent()) {
            return ResponseEntity.ok(new IngestionResult(stored.id(), header.controlId(), true, existingResource.get().resourceId()));
        }

        ParsedOru parsed = oruR01Extractor.extract(message);
        FhirResource resource = observationIngestionService.mapAndStore(parsed, stored.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IngestionResult(stored.id(), header.controlId(), false, resource.resourceId()));
    }

    private RawMessage persistRaw(RawMessage candidate, String controlId) {
        try {
            return rawMessageRepository.insert(candidate);
        } catch (DuplicateKeyException alreadyIngested) {
            return rawMessageRepository.findByControlId(controlId).orElseThrow(() -> alreadyIngested);
        }
    }

    private Message parseMessage(String rawPayload) {
        try {
            return hl7Parser.parse(rawPayload);
        } catch (HL7Exception e) {
            throw new UnparseableMessageException("mensagem HL7 malformada", e);
        }
    }

    // MSH-9 (message type ^ trigger event) and MSH-10 (control id) are the minimum a message
    // needs to be tracked and deduplicated.
    private MshHeader readHeader(Message message) {
        try {
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

    public record IngestionResult(Long id, String controlId, boolean duplicate, String observationId) {
    }
}
