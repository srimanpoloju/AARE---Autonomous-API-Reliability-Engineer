package com.aare.incidentapi.repository;

import com.aare.incidentapi.model.IncidentEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentEvidenceRepository extends JpaRepository<IncidentEvidence, UUID> {
    List<IncidentEvidence> findByIncidentId(UUID incidentId);
}
