package com.lanparty.dashboard.tournament;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.lanparty.dashboard.challonge.Bracket;

public final class TournamentDtos {

    private TournamentDtos() {
    }

    public enum StatusKind {
        LIVE, OPEN, PLANNED, CLOSED, DONE
    }

    public record Summary(Long id, String name, String color, String formatLabel, StatusKind statusKind,
                          String statusText, int maxParticipants, int registered, int teamSize,
                          boolean acceptsRegistrations, Instant registrationClosesAt, Instant startsAt,
                          String serverName, String challongeUrl, String rulesUrl) {
    }

    public record Detail(Summary summary, Bracket bracket, List<String> participants, Instant snapshotAt) {
    }

    public record RegistrationRequest(
            @NotBlank @Size(max = 60) String gamertag,
            @Size(max = 80) String teamName,
            @Size(max = 300) String teammates,
            @Size(max = 20) String seatLabel,
            boolean rulesAccepted) {
    }

    /** Admin view: summary plus fields only organisers need. */
    public record AdminView(Long id, String name, String color, String formatLabel, int maxParticipants, int teamSize,
                            boolean registrationOpen, Instant registrationClosesAt, Instant startsAt, Long serverId,
                            String challongeSlug, String rulesUrl, int sort, Instant snapshotAt,
                            List<AdminRegistration> registrations) {
    }

    public record AdminRegistration(Long id, String gamertag, String teamName, String teammates, String seatLabel,
                                    boolean syncedToChallonge, Instant createdAt) {
    }

    public record TournamentRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 20) String color,
            @Size(max = 200) String formatLabel,
            @Min(2) @Max(1024) int maxParticipants,
            @Min(1) @Max(20) int teamSize,
            boolean registrationOpen,
            Instant registrationClosesAt,
            Instant startsAt,
            Long serverId,
            @Size(max = 300) String challongeSlug,
            @Size(max = 300) String rulesUrl,
            int sort) {
    }
}
