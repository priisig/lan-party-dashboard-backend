package com.lanparty.dashboard.tournament;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    List<Registration> findByTournamentIdOrderByCreatedAt(Long tournamentId);

    long countByTournamentId(Long tournamentId);

    @Query("select r.tournamentId, count(r) from Registration r where r.tournamentId in :ids group by r.tournamentId")
    List<Object[]> countGrouped(List<Long> ids);
}
