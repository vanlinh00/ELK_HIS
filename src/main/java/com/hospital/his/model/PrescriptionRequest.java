package com.hospital.his.model;

import jakarta.validation.constraints.NotBlank;

public record PrescriptionRequest(
        @NotBlank(message = "drugName is required") String drugName,
        @NotBlank(message = "dosage is required") String dosage,
        String route,
        String clinicalNotes
) {}
