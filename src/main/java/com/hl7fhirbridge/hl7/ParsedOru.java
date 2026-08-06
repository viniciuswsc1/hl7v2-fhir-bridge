package com.hl7fhirbridge.hl7;

import java.time.LocalDate;

public record ParsedOru(PatientFields patient, ObservationFields observation) {

    public record PatientFields(
            String givenName,
            String familyName,
            LocalDate birthDate,
            String sex
    ) {
    }

    public record ObservationFields(
            String code,
            String display,
            String codingSystem,
            String valueType,
            String value,
            String unit,
            String referenceRange,
            String resultStatus
    ) {
    }
}
