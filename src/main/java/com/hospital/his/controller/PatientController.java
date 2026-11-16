package com.hospital.his.controller;

import com.hospital.his.model.PatientRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private static final Logger log = LoggerFactory.getLogger(PatientController.class);

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

    @GetMapping("/{patientId}/emr")
    public ResponseEntity<PatientRecord> getElectronicMedicalRecord(@PathVariable String patientId) {
        String doctorId = MDC.get("doctor_id");
        String department = MDC.get("department");

        MDC.put("event_type", "PATIENT_ACCESS");
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
            MDC.remove("event_type");
            MDC.remove("patient_id");
        }
    }
}
