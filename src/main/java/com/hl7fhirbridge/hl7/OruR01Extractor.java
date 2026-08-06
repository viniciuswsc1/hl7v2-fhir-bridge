package com.hl7fhirbridge.hl7;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import com.hl7fhirbridge.ingestion.UnparseableMessageException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Pulls PID and the first OBX out of an already-parsed ORU^R01 - one message maps to one
// Observation here, not one per result line.
//
// Terser's unprefixed "PID-5-1" only resolves segments that are a direct child of the
// message root. ORU_R01 nests PID/OBX inside groups (PATIENT_RESULT/PATIENT and
// PATIENT_RESULT/ORDER_OBSERVATION/OBSERVATION), so the full group path is needed.
@Component
public class OruR01Extractor {

    private static final DateTimeFormatter HL7_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String PID_PATH = "/PATIENT_RESULT/PATIENT/PID";
    private static final String OBX_PATH = "/PATIENT_RESULT/ORDER_OBSERVATION/OBSERVATION/OBX";

    public ParsedOru extract(Message message) {
        Terser terser = new Terser(message);
        return new ParsedOru(extractPatient(terser), extractObservation(terser));
    }

    private ParsedOru.PatientFields extractPatient(Terser terser) {
        try {
            String familyName = terser.get(PID_PATH + "-5-1-1");
            String givenName = terser.get(PID_PATH + "-5-2");
            if (isBlank(familyName) || isBlank(givenName)) {
                throw new UnparseableMessageException("PID-5 (nome do paciente) ausente ou incompleto");
            }
            LocalDate birthDate = parseDate(terser.get(PID_PATH + "-7-1"));
            String sex = terser.get(PID_PATH + "-8");
            return new ParsedOru.PatientFields(givenName, familyName, birthDate, sex);
        } catch (HL7Exception e) {
            throw new UnparseableMessageException("segmento PID malformado", e);
        }
    }

    private ParsedOru.ObservationFields extractObservation(Terser terser) {
        try {
            String code = terser.get(OBX_PATH + "-3-1");
            String value = terser.get(OBX_PATH + "-5");
            if (isBlank(code) || isBlank(value)) {
                throw new UnparseableMessageException("OBX-3 (codigo) ou OBX-5 (valor) ausente");
            }
            String display = terser.get(OBX_PATH + "-3-2");
            String codingSystem = terser.get(OBX_PATH + "-3-3");
            String valueType = terser.get(OBX_PATH + "-2");
            String unit = terser.get(OBX_PATH + "-6-1");
            String referenceRange = terser.get(OBX_PATH + "-7");
            String resultStatus = terser.get(OBX_PATH + "-11");
            return new ParsedOru.ObservationFields(
                    code, display, codingSystem,
                    isBlank(valueType) ? "ST" : valueType,
                    value, unit, referenceRange, resultStatus);
        } catch (HL7Exception e) {
            throw new UnparseableMessageException("segmento OBX malformado", e);
        }
    }

    private static LocalDate parseDate(String hl7Date) {
        if (isBlank(hl7Date)) {
            return null;
        }
        try {
            // HL7 TS fields are often longer than a date (time, timezone); truncate to YYYYMMDD.
            return LocalDate.parse(hl7Date.substring(0, 8), HL7_DATE);
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
