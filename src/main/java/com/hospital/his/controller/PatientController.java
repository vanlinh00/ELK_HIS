package com.hospital.his.controller;

import com.hospital.his.aspect.AuditableAction;
import com.hospital.his.model.PatientRecord;
import com.hospital.his.model.PrescriptionRequest;
import com.hospital.his.model.PrescriptionResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private static final Logger log = LoggerFactory.getLogger(PatientController.class);

    private final Set<String> controlledSubstances;

    private static final Map<String, PatientRecord> PATIENT_REGISTRY = Map.of(
            "PAT-1001", new PatientRecord(
                    "PAT-1001", "Nguyen Van A", "1984-05-14", "O+",
                    "Acute Bronchitis", List.of("Penicillin"), null, null
            ),
            "PAT-1002", new PatientRecord(
                    "PAT-1002", "Tran Thi B", "1972-11-03", "A-",
                    "Atrial Fibrillation", List.of("Sulfa Drugs"), null, null
            )
    );

    public PatientController(
            @Value("${his.audit.controlled-substances:Morphine,Fentanyl,Oxycodone,Midazolam,Hydromorphone}")
            List<String> controlledSubstances) {
        this.controlledSubstances = controlledSubstances.stream()
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toSet());
    }

    @GetMapping("/{patientId}/emr")
    @AuditableAction(eventType = "PATIENT_ACCESS", resource = "EMR")
    public ResponseEntity<PatientRecord> getElectronicMedicalRecord(@PathVariable String patientId) {
        String doctorId = MDC.get("doctor_id");
        String department = MDC.get("department");

        MDC.put("patient_id", patientId);
        try {
            log.info("Electronic Medical Record (EMR) accessed for patient {}", patientId);

            PatientRecord base = PATIENT_REGISTRY.getOrDefault(
                    patientId,
                    new PatientRecord(
                            patientId, "Clinical Demo Patient", "1990-01-01", "B+",
                            "Observation", List.of(), null, null
                    )
            );

            return ResponseEntity.ok(new PatientRecord(
                    base.patientId(),
                    base.fullName(),
                    base.dateOfBirth(),
                    base.bloodType(),
                    base.primaryDiagnosis(),
                    base.allergies(),
                    doctorId,
                    department
            ));
        } finally {
            MDC.remove("patient_id");
        }
    }

    @PostMapping("/{patientId}/prescriptions")
    @AuditableAction(eventType = "PRESCRIPTION_ORDER", resource = "PRESCRIPTION")
    public ResponseEntity<PrescriptionResponse> createPrescription(
            @PathVariable String patientId,
            @Valid @RequestBody PrescriptionRequest request) {

        String doctorId = MDC.get("doctor_id");
        String department = MDC.get("department");
        boolean isControlled = controlledSubstances.contains(request.drugName().trim().toLowerCase());

        MDC.put("patient_id", patientId);
        MDC.put("drug_name", request.drugName());
        MDC.put("dosage", request.dosage());

        try {
            if (isControlled) {
                log.warn("Controlled substance prescribed: {} ({}) for patient {}",
                        request.drugName(), request.dosage(), patientId);
            } else {
                log.info("Prescription created: {} ({}) for patient {}",
                        request.drugName(), request.dosage(), patientId);
            }

            return ResponseEntity.ok(new PrescriptionResponse(
                    "RX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    "PRESCRIBED",
                    patientId,
                    request.drugName(),
                    request.dosage(),
                    isControlled,
                    doctorId,
                    department,
                    Instant.now()
            ));
        } finally {
            MDC.remove("patient_id");
            MDC.remove("drug_name");
            MDC.remove("dosage");
        }
    }
}
