package com.lanparty.dashboard.seating;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRowRepository extends JpaRepository<SeatRow, Long> {

    List<SeatRow> findByEventIdOrderBySort(Long eventId);
}
