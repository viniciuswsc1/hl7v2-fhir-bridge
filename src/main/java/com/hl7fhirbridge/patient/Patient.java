package com.hl7fhirbridge.patient;

import com.hl7fhirbridge.hl7.ParsedOru;

import java.time.LocalDate;
import java.time.OffsetDateTime;

// TODO(MPI): every ingestion creates a new row here, no deduplication (CNS -> CPF -> composite
// key) yet. cns/cpf aren't even extracted from PID-3 yet, left null on purpose.
public record Patient(
        Long id,
        OffsetDateTime createdAt,
        String givenName,
        String familyName,
        LocalDate birthDate,
        String sex,
        String cns,
        String cpf
) {

    public static Patient from(ParsedOru.PatientFields fields) {
        return new Patient(null, null, fields.givenName(), fields.familyName(), fields.birthDate(), fields.sex(), null, null);
    }
}
