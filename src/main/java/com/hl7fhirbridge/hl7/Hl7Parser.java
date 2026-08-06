package com.hl7fhirbridge.hl7;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import org.springframework.stereotype.Component;

@Component
public class Hl7Parser {

    private final PipeParser pipeParser = new PipeParser();

    public Message parse(String rawMessage) throws HL7Exception {
        return pipeParser.parse(rawMessage);
    }
}
