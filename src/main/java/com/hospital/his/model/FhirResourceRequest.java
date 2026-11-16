package com.hospital.his.model;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record FhirResourceRequest(
        @NotBlank(message = "resourceType is required") String resourceType,
        @NotBlank(message = "id is required") String id,
        String patientId,
        String loincCode,
        Map<String, Object> payload
) {}
