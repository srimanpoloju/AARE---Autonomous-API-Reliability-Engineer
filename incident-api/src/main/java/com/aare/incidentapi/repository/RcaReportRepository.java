package com.aare.incidentapi.repository;

import com.aare.incidentapi.model.RcaReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RcaReportRepository extends JpaRepository<RcaReport, UUID> {
    Optional<RcaReport> findByIncidentId(UUID incidentId);
}
