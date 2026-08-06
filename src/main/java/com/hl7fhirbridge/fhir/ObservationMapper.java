package com.hl7fhirbridge.fhir;

import com.hl7fhirbridge.hl7.ParsedOru;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;

import java.math.BigDecimal;

// OBX-3 is used as-is for Observation.code (system = OBX-3.3, raw). No local-code -> LOINC
// translation or quarantine for unmapped codes yet.
public final class ObservationMapper {

    private ObservationMapper() {
    }

    public static Observation map(String resourceId, long patientId, ParsedOru.ObservationFields fields) {
        Observation observation = new Observation();
        observation.setId(resourceId);
        observation.setStatus(mapStatus(fields.resultStatus()));
        observation.setCode(mapCode(fields));
        observation.setSubject(new Reference("Patient/" + patientId));

        observation.setValue(mapValue(fields));

        if (fields.referenceRange() != null && !fields.referenceRange().isBlank()) {
            observation.addReferenceRange().setText(fields.referenceRange());
        }

        return observation;
    }

    private static CodeableConcept mapCode(ParsedOru.ObservationFields fields) {
        CodeableConcept code = new CodeableConcept();
        var coding = code.addCoding().setCode(fields.code()).setDisplay(fields.display());
        if (fields.codingSystem() != null && !fields.codingSystem().isBlank()) {
            coding.setSystem(fields.codingSystem());
        }
        return code;
    }

    private static Type mapValue(ParsedOru.ObservationFields fields) {
        if (!"NM".equalsIgnoreCase(fields.valueType())) {
            return new StringType(fields.value());
        }
        try {
            Quantity quantity = new Quantity().setValue(new BigDecimal(fields.value()));
            if (fields.unit() != null && !fields.unit().isBlank()) {
                quantity.setUnit(fields.unit());
            }
            return quantity;
        } catch (NumberFormatException e) {
            // OBX-2 said "NM" but the value isn't actually numeric; fall back to free text
            // rather than failing the whole ingestion over it.
            return new StringType(fields.value());
        }
    }

    private static Observation.ObservationStatus mapStatus(String resultStatus) {
        if (resultStatus == null) {
            return Observation.ObservationStatus.UNKNOWN;
        }
        return switch (resultStatus.toUpperCase()) {
            case "F" -> Observation.ObservationStatus.FINAL;
            case "P" -> Observation.ObservationStatus.PRELIMINARY;
            case "C" -> Observation.ObservationStatus.CORRECTED;
            case "X" -> Observation.ObservationStatus.CANCELLED;
            default -> Observation.ObservationStatus.UNKNOWN;
        };
    }
}
