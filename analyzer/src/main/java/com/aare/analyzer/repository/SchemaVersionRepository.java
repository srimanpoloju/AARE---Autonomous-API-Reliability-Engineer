package com.aare.analyzer.repository;

import com.aare.analyzer.model.SchemaVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchemaVersionRepository extends JpaRepository<SchemaVersion, Long> {
    Optional<SchemaVersion> findTopByEndpointIdOrderByVersionDesc(String endpointId);
    List<SchemaVersion> findByEndpointIdOrderByVersionDesc(String endpointId);
}
