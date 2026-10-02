package com.lanparty.dashboard.event;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findFirstByActiveTrue();

    boolean existsBySlug(String slug);

    List<Event> findAllByOrderByStartsAtDesc();

    @Modifying
    @Query("update Event e set e.active = false where e.active = true")
    void deactivateAll();
}
