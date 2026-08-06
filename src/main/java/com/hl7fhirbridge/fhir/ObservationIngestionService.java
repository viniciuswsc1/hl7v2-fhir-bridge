package com.hl7fhirbridge.fhir;

import ca.uhn.fhir.context.FhirContext;
import com.hl7fhirbridge.hl7.ParsedOru;
import com.hl7fhirbridge.patient.Patient;
import com.hl7fhirbridge.patient.PatientRepository;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Own transaction, separate from the raw_message insert that already happened by the
// time this runs: if mapping fails here, the raw message stays durable and only
// patient+fhir_resource roll back.
@Component
public class ObservationIngestionService {

    private final PatientRepository patientRepository;
    private final FhirResourceRepository fhirResourceRepository;
    private final FhirContext fhirContext = FhirContext.forR4();

    public ObservationIngestionService(PatientRepository patientRepository, FhirResourceRepository fhirResourceRepository) {
        this.patientRepository = patientRepository;
        this.fhirResourceRepository = fhirResourceRepository;
    }

    @Transactional
    public FhirResource mapAndStore(ParsedOru parsed, long sourceMessageId) {
        Patient patient = patientRepository.insert(Patient.from(parsed.patient()));

        String resourceId = UUID.randomUUID().toString();
        Observation observation = ObservationMapper.map(resourceId, patient.id(), parsed.observation());
        String content = fhirContext.newJsonParser().encodeResourceToString(observation);

        return fhirResourceRepository.insert(FhirResource.capture("Observation", resourceId, content, sourceMessageId));
    }
}
