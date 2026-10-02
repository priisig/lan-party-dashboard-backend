package com.lanparty.dashboard.schedule;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class ScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private Instant startsAt;
    private Instant endsAt;
    private String title;
    private String location;
    private String color = "#6B6390";
    private Long tournamentId;

    protected ScheduleItem() {
    }

    public ScheduleItem(Long eventId, Instant startsAt, Instant endsAt, String title, String location, String color, Long tournamentId) {
        this.eventId = eventId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.title = title;
        this.location = location;
        this.color = color;
        this.tournamentId = tournamentId;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public String getColor() { return color; }
    public Long getTournamentId() { return tournamentId; }
}
