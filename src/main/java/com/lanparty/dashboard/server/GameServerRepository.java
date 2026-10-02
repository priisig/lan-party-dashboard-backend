package com.lanparty.dashboard.server;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameServerRepository extends JpaRepository<GameServer, Long> {

    List<GameServer> findByEventIdOrderBySort(Long eventId);
}
