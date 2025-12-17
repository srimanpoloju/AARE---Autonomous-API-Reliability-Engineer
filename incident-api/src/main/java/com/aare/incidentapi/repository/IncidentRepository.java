package com.aare.incidentapi.repository;

import com.aare.incidentapi.model.Incident;
import com.aare.incidentapi.model.IncidentStatus;
import com.aare.incidentapi.model.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByStatus(IncidentStatus status);
    List<Incident> findByType(IncidentType type);
    List<Incident> findByStatusAndType(IncidentStatus status, IncidentType type);
}
