package com.hospital.his;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
