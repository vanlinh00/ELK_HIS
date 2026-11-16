package com.hospital.his.model;

import java.util.List;

public record PatientRecord(
        String patientId,
        String fullName,
        String dateOfBirth,
        String bloodType,
        String primaryDiagnosis,
        List<String> allergies,
        String accessedByDoctorId,
        String department
) {}
