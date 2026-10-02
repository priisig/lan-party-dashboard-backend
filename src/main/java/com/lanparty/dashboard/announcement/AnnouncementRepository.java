package com.lanparty.dashboard.announcement;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByEventIdOrderBySort(Long eventId);

    void deleteByEventId(Long eventId);
}
