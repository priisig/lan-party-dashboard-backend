package com.lanparty.dashboard.tournament;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    List<Tournament> findByEventIdOrderBySort(Long eventId);
}
