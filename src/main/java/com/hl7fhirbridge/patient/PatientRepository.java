package com.hl7fhirbridge.patient;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PatientRepository {

    private final JdbcClient jdbcClient;

    public PatientRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Patient insert(Patient patient) {
        return jdbcClient.sql("""
                        INSERT INTO patient (given_name, family_name, birth_date, sex, cns, cpf)
                        VALUES (:givenName, :familyName, :birthDate, :sex, :cns, :cpf)
                        RETURNING id, created_at, given_name, family_name, birth_date, sex, cns, cpf
                        """)
                .param("givenName", patient.givenName())
                .param("familyName", patient.familyName())
                .param("birthDate", patient.birthDate())
                .param("sex", patient.sex())
                .param("cns", patient.cns())
                .param("cpf", patient.cpf())
                .query(Patient.class)
                .single();
    }
}
