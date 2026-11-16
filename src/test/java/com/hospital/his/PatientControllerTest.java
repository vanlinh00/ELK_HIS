package com.hospital.his;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void emrAccessExtractsHeaderMdcIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/patients/PAT-1001/emr")
                        .header("X-Doctor-Id", "DR-402")
                        .header("X-Department", "CARDIOLOGY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value("PAT-1001"))
                .andExpect(jsonPath("$.accessedByDoctorId").value("DR-402"))
                .andExpect(jsonPath("$.department").value("CARDIOLOGY"));
    }

    @Test
    void controlledSubstancePrescriptionFlagsControlledStatus() throws Exception {
        mockMvc.perform(post("/api/v1/patients/PAT-1001/prescriptions")
                        .header("X-Doctor-Id", "DR-402")
                        .header("X-Department", "CARDIOLOGY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drugName\":\"Morphine\",\"dosage\":\"5mg IV\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESCRIBED"))
                .andExpect(jsonPath("$.controlledSubstance").value(true))
                .andExpect(jsonPath("$.orderedByDoctorId").value("DR-402"));
    }

    @Test
    void hl7AndFhirInteroperabilityEndpointsAcceptClinicalPayloads() throws Exception {
        mockMvc.perform(post("/api/v1/interoperability/hl7")
                        .header("X-Doctor-Id", "DR-108")
                        .header("X-Department", "EMERGENCY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawMessage\":\"MSH|^~\\\\&|HIS|HOSP|ELK|AUDIT|202611152300||ADT^A01|MSG001|P|2.5\\nPID|1||PAT-1001||NGUYEN^VAN^A||19840514|M\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACK"))
                .andExpect(jsonPath("$.patientId").value("PAT-1001"));

        mockMvc.perform(post("/api/v1/interoperability/fhir")
                        .header("X-Doctor-Id", "DR-108")
                        .header("X-Department", "EMERGENCY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"Observation\",\"id\":\"OBS-991\",\"patientId\":\"PAT-1001\",\"loincCode\":\"8867-4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INGESTED"))
                .andExpect(jsonPath("$.resourceType").value("Observation"));
    }
}
