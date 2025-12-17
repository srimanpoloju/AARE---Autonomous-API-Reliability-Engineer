package com.aare.analyzer.repository;

import com.aare.analyzer.model.IncidentEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentEvidenceRepository extends JpaRepository<IncidentEvidence, UUID> {
    List<IncidentEvidence> findByIncidentId(UUID incidentId);
}
