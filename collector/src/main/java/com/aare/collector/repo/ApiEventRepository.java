package com.aare.collector.repo;

import com.aare.collector.model.ApiEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApiEventRepository extends JpaRepository<ApiEvent, UUID> {
}
