package com.lanparty.dashboard.seating;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRequestRepository extends JpaRepository<SeatRequest, Long> {

    List<SeatRequest> findBySeatIdInAndStatusOrderByCreatedAt(Collection<Long> seatIds, RequestStatus status);

    boolean existsBySeatIdAndStatus(Long seatId, RequestStatus status);

    List<SeatRequest> findByUserIdAndStatus(Long userId, RequestStatus status);
}
