package com.aare.analyzer.repository;

import com.aare.analyzer.model.Incident;
import com.aare.analyzer.model.IncidentStatus;
import com.aare.analyzer.model.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    List<Incident> findByEndpointIdAndTypeAndStatusAndDetectedAtBetween(
            String endpointId,
            IncidentType type,
            IncidentStatus status,
            LocalDateTime start,
            LocalDateTime end
    );
}
