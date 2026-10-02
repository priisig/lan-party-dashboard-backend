package com.lanparty.dashboard.seating;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventId(Long eventId);

    Optional<Seat> findByEventIdAndLabelIgnoreCase(Long eventId, String label);

    Optional<Seat> findFirstByEventIdAndUserId(Long eventId, Long userId);
}
