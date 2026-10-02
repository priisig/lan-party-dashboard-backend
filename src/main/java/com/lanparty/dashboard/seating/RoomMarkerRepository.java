package com.lanparty.dashboard.seating;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMarkerRepository extends JpaRepository<RoomMarker, Long> {

    List<RoomMarker> findByEventIdOrderBySort(Long eventId);

    void deleteByEventId(Long eventId);
}
