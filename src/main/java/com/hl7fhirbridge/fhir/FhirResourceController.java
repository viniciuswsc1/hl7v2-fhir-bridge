package com.hl7fhirbridge.fhir;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FhirResourceController {

    private final FhirResourceRepository fhirResourceRepository;

    public FhirResourceController(FhirResourceRepository fhirResourceRepository) {
        this.fhirResourceRepository = fhirResourceRepository;
    }

    // content is already a serialized FHIR resource, produced at ingestion time by
    // ObservationMapper + HAPI's JSON encoder. Return it as a String body, not through
    // Jackson again, or it gets double-encoded.
    @GetMapping(value = "/fhir/Observation/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getObservation(@PathVariable String id) {
        return fhirResourceRepository.findByTypeAndResourceId("Observation", id)
                .map(resource -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resource.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
