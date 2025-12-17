package com.aare.collector.repo;

import com.aare.collector.model.ApiEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApiEventRepository extends JpaRepository<ApiEvent, UUID> {
}
