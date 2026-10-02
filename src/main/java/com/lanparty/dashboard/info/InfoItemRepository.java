package com.lanparty.dashboard.info;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InfoItemRepository extends JpaRepository<InfoItem, Long> {

    List<InfoItem> findByEventIdOrderBySort(Long eventId);

    void deleteByEventId(Long eventId);
}
