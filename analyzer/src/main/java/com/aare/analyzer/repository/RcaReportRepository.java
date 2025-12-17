package com.aare.analyzer.repository;

import com.aare.analyzer.model.RcaReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RcaReportRepository extends JpaRepository<RcaReport, Long> {
}
