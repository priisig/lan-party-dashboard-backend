package com.lanparty.dashboard.schedule;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    List<ScheduleItem> findByEventIdOrderByStartsAt(Long eventId);

    void deleteByEventId(Long eventId);
}
