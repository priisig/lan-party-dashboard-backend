package com.lanparty.dashboard.tournament;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private String name;
    private String color = "#9B5CFF";
    private String formatLabel;
    private int maxParticipants = 16;
    private int teamSize = 1;
    private boolean registrationOpen;
    private Instant registrationClosesAt;
    private Instant startsAt;
    private Long serverId;
    private String challongeSlug;
    /** Last raw Challonge response, kept so the bracket still renders when the internet is down. */
    private String challongeSnapshot;
    private Instant snapshotAt;
    private String rulesUrl;
    private int sort;

    /** Registration is possible when the flag is on and the closing time (if any) has not passed. */
    public boolean acceptsRegistrations(Instant now) {
        return registrationOpen && (registrationClosesAt == null || now.isBefore(registrationClosesAt));
    }

    public boolean isTeamTournament() {
        return teamSize > 1;
    }

    public String challongeUrl() {
        return challongeSlug == null || challongeSlug.isBlank() ? null : "https://challonge.com/" + challongeSlug;
    }

    /** Copies the setup for next year's event; registrations, Challonge link and dates are not carried over. */
    public Tournament copyTo(Long targetEventId) {
        Tournament copy = new Tournament();
        copy.eventId = targetEventId;
        copy.name = name;
        copy.color = color;
        copy.formatLabel = formatLabel;
        copy.maxParticipants = maxParticipants;
        copy.teamSize = teamSize;
        copy.rulesUrl = rulesUrl;
        copy.sort = sort;
        return copy;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getFormatLabel() { return formatLabel; }
    public void setFormatLabel(String formatLabel) { this.formatLabel = formatLabel; }
    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }
    public int getTeamSize() { return teamSize; }
    public void setTeamSize(int teamSize) { this.teamSize = teamSize; }
    public boolean isRegistrationOpen() { return registrationOpen; }
    public void setRegistrationOpen(boolean registrationOpen) { this.registrationOpen = registrationOpen; }
    public Instant getRegistrationClosesAt() { return registrationClosesAt; }
    public void setRegistrationClosesAt(Instant registrationClosesAt) { this.registrationClosesAt = registrationClosesAt; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public String getChallongeSlug() { return challongeSlug; }
    public void setChallongeSlug(String challongeSlug) { this.challongeSlug = challongeSlug; }
    public String getChallongeSnapshot() { return challongeSnapshot; }
    public void setChallongeSnapshot(String challongeSnapshot) { this.challongeSnapshot = challongeSnapshot; }
    public Instant getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(Instant snapshotAt) { this.snapshotAt = snapshotAt; }
    public String getRulesUrl() { return rulesUrl; }
    public void setRulesUrl(String rulesUrl) { this.rulesUrl = rulesUrl; }
    public int getSort() { return sort; }
    public void setSort(int sort) { this.sort = sort; }
}
