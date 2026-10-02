package com.lanparty.dashboard.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByEventIdAndUserId(Long eventId, Long userId);

    List<Participant> findByEventId(Long eventId);

    long countByUserId(Long userId);
}
