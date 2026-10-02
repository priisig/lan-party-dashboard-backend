package com.lanparty.dashboard.stats;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricRepository extends JpaRepository<Metric, Long> {

    List<Metric> findByEventIdOrderBySortAscKeyAsc(Long eventId);

    Optional<Metric> findByEventIdAndKey(Long eventId, String key);
}
