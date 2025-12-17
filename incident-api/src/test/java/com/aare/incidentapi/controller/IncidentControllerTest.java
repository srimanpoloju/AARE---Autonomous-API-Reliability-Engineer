package com.aare.incidentapi.controller;

import com.aare.incidentapi.model.*;
import com.aare.incidentapi.repository.IncidentEvidenceRepository;
import com.aare.incidentapi.repository.IncidentRepository;
import com.aare.incidentapi.repository.RcaReportRepository;
import com.aare.incidentapi.service.JwtService;
import com.aare.incidentapi.service.UserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IncidentController.class)
// Import security config for full context, otherwise SecurityFilterChain will not be configured
@Import({com.aare.incidentapi.config.SecurityConfig.class, com.aare.incidentapi.filter.JwtRequestFilter.class})
public class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IncidentRepository incidentRepository;

    @MockBean
    private IncidentEvidenceRepository incidentEvidenceRepository;

    @MockBean
    private RcaReportRepository rcaReportRepository;

    // MockBeans for security components
    @MockBean
    private JwtService jwtService;
    @MockBean
    private UserDetailsService userDetailsService; // Note: this UserDetailsService is custom, not Spring's default

    private Incident testIncident;
    private IncidentEvidence testEvidence;
    private RcaReport testRcaReport;

    @BeforeEach
    void setUp() {
        testIncident = new Incident();
        testIncident.setId(UUID.randomUUID());
        testIncident.setEndpointId("GET:/api/test");
        testIncident.setType(IncidentType.ERROR_SPIKE);
        testIncident.setStatus(IncidentStatus.OPEN);
        testIncident.setSeverity(IncidentSeverity.HIGH);
        testIncident.setDetectedAt(LocalDateTime.now());
        testIncident.setTriggeredAt(LocalDateTime.now().minusMinutes(1));

        testEvidence = new IncidentEvidence();
        testEvidence.setId(UUID.randomUUID());
        testEvidence.setIncidentId(testIncident.getId());
        testEvidence.setEvidenceType(EvidenceType.METRICS);
        testEvidence.setData(Collections.singletonMap("errorRate", 0.5));
        testEvidence.setCreatedAt(LocalDateTime.now());

        testRcaReport = new RcaReport();
        testRcaReport.setId(UUID.randomUUID());
        testRcaReport.setIncidentId(testIncident.getId());
        testRcaReport.setStatus(RcaStatus.GENERATED);
        testRcaReport.setRootCauseSummary("Test RCA Summary");
    }

    @Test
    @WithMockUser // Simulate authenticated user
    void getHealthCheck_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Incident API is healthy"));
    }

    @Test
    @WithMockUser
    void getIncidents_shouldReturnAllIncidents() throws Exception {
        when(incidentRepository.findAll()).thenReturn(List.of(testIncident));

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(testIncident.getId().toString()))
                .andExpect(jsonPath("$[0].type").value(testIncident.getType().toString()));
    }

    @Test
    @WithMockUser
    void getIncidentById_shouldReturnIncident() throws Exception {
        when(incidentRepository.findById(testIncident.getId())).thenReturn(Optional.of(testIncident));

        mockMvc.perform(get("/api/incidents/{id}", testIncident.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testIncident.getId().toString()));
    }

    @Test
    @WithMockUser
    void getIncidentById_shouldReturnNotFound() throws Exception {
        when(incidentRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/incidents/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getIncidentEvidence_shouldReturnEvidence() throws Exception {
        when(incidentEvidenceRepository.findByIncidentId(testIncident.getId())).thenReturn(List.of(testEvidence));

        mockMvc.perform(get("/api/incidents/{id}/evidence", testIncident.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(testEvidence.getId().toString()))
                .andExpect(jsonPath("$[0].evidenceType").value(testEvidence.getEvidenceType().toString()));
    }

    @Test
    @WithMockUser
    void getRcaReport_shouldReturnRcaReport() throws Exception {
        when(rcaReportRepository.findByIncidentId(testIncident.getId())).thenReturn(Optional.of(testRcaReport));

        mockMvc.perform(get("/api/incidents/{id}/rca", testIncident.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testRcaReport.getId().toString()))
                .andExpect(jsonPath("$.rootCauseSummary").value(testRcaReport.getRootCauseSummary()));
    }

    @Test
    @WithMockUser
    void acknowledgeIncident_shouldUpdateStatusToAcknowledged() throws Exception {
        when(incidentRepository.findById(testIncident.getId())).thenReturn(Optional.of(testIncident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            incident.setStatus(IncidentStatus.ACKNOWLEDGED);
            return incident;
        });

        mockMvc.perform(post("/api/incidents/{id}/ack", testIncident.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(IncidentStatus.ACKNOWLEDGED.toString()));
    }

    @Test
    @WithMockUser
    void acknowledgeIncident_shouldReturnNotFound_whenIncidentDoesNotExist() throws Exception {
        when(incidentRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/incidents/{id}/ack", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
    
    @Test
    @WithMockUser
    void resolveIncident_shouldUpdateStatusToResolved() throws Exception {
        testIncident.setStatus(IncidentStatus.ACKNOWLEDGED); // Can resolve from ACKNOWLEDGED
        when(incidentRepository.findById(testIncident.getId())).thenReturn(Optional.of(testIncident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            incident.setStatus(IncidentStatus.RESOLVED);
            return incident;
        });

        mockMvc.perform(post("/api/incidents/{id}/resolve", testIncident.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(IncidentStatus.RESOLVED.toString()));
    }

    @Test
    @WithMockUser
    void resolveIncident_shouldReturnNotFound_whenIncidentDoesNotExist() throws Exception {
        when(incidentRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/incidents/{id}/resolve", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}