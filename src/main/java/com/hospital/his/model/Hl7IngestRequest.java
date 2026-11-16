package com.hospital.his.model;

import jakarta.validation.constraints.NotBlank;

public record Hl7IngestRequest(
        @NotBlank(message = "rawMessage is required") String rawMessage
) {}
