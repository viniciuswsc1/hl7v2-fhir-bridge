package com.hl7fhirbridge;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hl7fhirbridge.ingestion.MessageController.IngestionResult;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MessageIngestionIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ingestsOruR01AndServesTheResultingObservationBackAsValidFhirJson() throws IOException {
        String rawMessage = readResource("sample-oru-r01.hl7");

        ResponseEntity<String> postResponse = post(rawMessage);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        IngestionResult ingestionResult = objectMapper.readValue(postResponse.getBody(), IngestionResult.class);
        assertThat(ingestionResult.duplicate()).isFalse();
        assertThat(ingestionResult.observationId()).isNotBlank();

        ResponseEntity<String> getResponse = restTemplate.getForEntity(
                "/fhir/Observation/{id}", String.class, ingestionResult.observationId());
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Observation observation = (Observation) FHIR_CONTEXT.newJsonParser()
                .parseResource(getResponse.getBody());
        assertThat(observation.getIdPart()).isEqualTo(ingestionResult.observationId());
        assertThat(observation.getStatus()).isEqualTo(Observation.ObservationStatus.FINAL);
        assertThat(observation.getCode().getCodingFirstRep().getCode()).isEqualTo("789-8");
        assertThat(observation.getValueQuantity().getValue().doubleValue()).isEqualTo(13.5);
    }

    @Test
    void sameMessageSentTwiceDoesNotCreateASecondObservation() throws IOException {
        String rawMessage = readResource("sample-oru-r01.hl7")
                .replace("MSG00001", "MSG-DUPLICATE-TEST");

        ResponseEntity<String> first = post(rawMessage);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        IngestionResult firstResult = objectMapper.readValue(first.getBody(), IngestionResult.class);

        ResponseEntity<String> second = post(rawMessage);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        IngestionResult secondResult = objectMapper.readValue(second.getBody(), IngestionResult.class);

        assertThat(secondResult.duplicate()).isTrue();
        assertThat(secondResult.observationId()).isEqualTo(firstResult.observationId());
    }

    @Test
    void malformedMessageIsRejectedWithoutLeakingPatientData() {
        ResponseEntity<String> response = post("isso nao e uma mensagem HL7 valida, SILVA JOAO 19800101");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).doesNotContainIgnoringCase("SILVA").doesNotContainIgnoringCase("JOAO");
    }

    private ResponseEntity<String> post(String rawPayload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return restTemplate.postForEntity("/messages", new HttpEntity<>(rawPayload, headers), String.class);
    }

    private static String readResource(String name) throws IOException {
        try (InputStream in = MessageIngestionIntegrationTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("resource not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
