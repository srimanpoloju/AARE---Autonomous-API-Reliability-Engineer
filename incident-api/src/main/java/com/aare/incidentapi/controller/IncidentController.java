package com.aare.incidentapi.controller;

import com.aare.incidentapi.model.*;
import com.aare.incidentapi.repository.IncidentEvidenceRepository;
import com.aare.incidentapi.repository.IncidentRepository;
import com.aare.incidentapi.repository.RcaReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class IncidentController {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentEvidenceRepository incidentEvidenceRepository;

    @Autowired
    private RcaReportRepository rcaReportRepository;

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Incident API is healthy");
    }

    @GetMapping("/incidents")
    public ResponseEntity<List<Incident>> getIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentType type) {

        if (status != null && type != null) {
            return ResponseEntity.ok(incidentRepository.findByStatusAndType(status, type));
        } else if (status != null) {
            return ResponseEntity.ok(incidentRepository.findByStatus(status));
        } else if (type != null) {
            return ResponseEntity.ok(incidentRepository.findByType(type));
        } else {
            return ResponseEntity.ok(incidentRepository.findAll());
        }
    }

    @GetMapping("/incidents/{id}")
    public ResponseEntity<Incident> getIncidentById(@PathVariable UUID id) {
        return incidentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/incidents/{id}/evidence")
    public ResponseEntity<List<IncidentEvidence>> getIncidentEvidence(@PathVariable UUID id) {
        return ResponseEntity.ok(incidentEvidenceRepository.findByIncidentId(id));
    }

    @GetMapping("/incidents/{id}/rca")
    public ResponseEntity<RcaReport> getRcaReport(@PathVariable UUID id) {
        return rcaReportRepository.findByIncidentId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/incidents/{id}/ack")
    public ResponseEntity<Incident> acknowledgeIncident(@PathVariable UUID id) {
        Optional<Incident> incidentOpt = incidentRepository.findById(id);
        if (incidentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Incident incident = incidentOpt.get();
        if (incident.getStatus() == IncidentStatus.OPEN) {
            incident.setStatus(IncidentStatus.ACKNOWLEDGED);
            incident.setAcknowledgedAt(LocalDateTime.now());
            return ResponseEntity.ok(incidentRepository.save(incident));
        }
        return ResponseEntity.status(409).body(incident); // 409 Conflict
    }

    @PostMapping("/incidents/{id}/resolve")
    public ResponseEntity<Incident> resolveIncident(@PathVariable UUID id) {
        Optional<Incident> incidentOpt = incidentRepository.findById(id);
        if (incidentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Incident incident = incidentOpt.get();
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(LocalDateTime.now());
        return ResponseEntity.ok(incidentRepository.save(incident));
    }
}
