package com.hospital.his.controller;

import com.hospital.his.aspect.AuditableAction;
import com.hospital.his.model.FhirResourceRequest;
import com.hospital.his.model.Hl7IngestRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/interoperability")
public class InteroperabilityController {

    private static final Logger log = LoggerFactory.getLogger(InteroperabilityController.class);

    @PostMapping("/hl7")
    @AuditableAction(eventType = "HL7_MESSAGE_PROCESSED", resource = "HL7_V2")
    public ResponseEntity<Map<String, Object>> ingestHl7Message(@Valid @RequestBody Hl7IngestRequest request) {
        String raw = request.rawMessage().trim();
        Map<String, String> parsedSummary = parseHl7Summary(raw);

        String messageType = parsedSummary.getOrDefault("messageType", "UNKNOWN");
        String patientId = parsedSummary.getOrDefault("patientId", "UNKNOWN");

        MDC.put("hl7_raw", raw);
        MDC.put("hl7_message_type", messageType);
        MDC.put("patient_id", patientId);

        try {
            log.info("HL7 v2 message ingested: type={} patientId={}", messageType, patientId);
            return ResponseEntity.ok(Map.of(
                    "status", "ACK",
                    "protocol", "HL7v2",
                    "messageType", messageType,
                    "patientId", patientId,
                    "processedAt", Instant.now().toString()
            ));
        } finally {
            MDC.remove("hl7_raw");
            MDC.remove("hl7_message_type");
            MDC.remove("patient_id");
        }
    }

    @PostMapping("/fhir")
    @AuditableAction(eventType = "FHIR_RESOURCE_INGESTED", resource = "FHIR_R4")
    public ResponseEntity<Map<String, Object>> ingestFhirResource(@Valid @RequestBody FhirResourceRequest request) {
        String patientId = request.patientId() != null && !request.patientId().isBlank()
                ? request.patientId()
                : "UNKNOWN";

        MDC.put("fhir_resource_type", request.resourceType());
        MDC.put("fhir_resource_id", request.id());
        MDC.put("patient_id", patientId);
        if (request.loincCode() != null && !request.loincCode().isBlank()) {
            MDC.put("fhir_loinc_code", request.loincCode());
        }

        try {
            log.info("FHIR R4 resource ingested: {}/{} for patient {}",
                    request.resourceType(), request.id(), patientId);
            return ResponseEntity.ok(Map.of(
                    "status", "INGESTED",
                    "protocol", "FHIR_R4",
                    "resourceType", request.resourceType(),
                    "resourceId", request.id(),
                    "patientId", patientId,
                    "processedAt", Instant.now().toString()
            ));
        } finally {
            MDC.remove("fhir_resource_type");
            MDC.remove("fhir_resource_id");
            MDC.remove("fhir_loinc_code");
            MDC.remove("patient_id");
        }
    }

    private Map<String, String> parseHl7Summary(String raw) {
        Map<String, String> summary = new LinkedHashMap<>();
        String[] segments = raw.split("\\r\\n|\\r|\\n|\\\\n");
        for (String segment : segments) {
            String[] fields = segment.split("\\|", -1);
            if (fields.length == 0) {
                continue;
            }
            if ("MSH".equals(fields[0]) && fields.length > 8) {
                summary.put("messageType", fields[8]);
            } else if ("PID".equals(fields[0]) && fields.length > 3) {
                summary.put("patientId", fields[3].split("\\^")[0]);
            }
        }
        return summary;
    }
}
