package com.hospital.his.model;

import java.time.Instant;

public record PrescriptionResponse(
        String prescriptionId,
        String status,
        String patientId,
        String drugName,
        String dosage,
        boolean controlledSubstance,
        String orderedByDoctorId,
        String department,
        Instant orderedAt
) {}
