package com.lanparty.dashboard.stats;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    List<Integration> findByEventIdOrderBySort(Long eventId);
}
