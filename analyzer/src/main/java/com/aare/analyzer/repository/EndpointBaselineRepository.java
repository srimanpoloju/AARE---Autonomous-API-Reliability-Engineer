package com.aare.analyzer.repository;

import com.aare.analyzer.model.EndpointBaseline;
import com.aare.analyzer.model.MetricWindowType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EndpointBaselineRepository extends JpaRepository<EndpointBaseline, Long> {
    Optional<EndpointBaseline> findByEndpointIdAndMetricWindow(String endpointId, MetricWindowType metricWindow);
}
