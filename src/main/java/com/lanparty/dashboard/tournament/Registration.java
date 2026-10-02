package com.lanparty.dashboard.tournament;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tournamentId;
    private String gamertag;
    private String teamName;
    private String teammates;
    private String seatLabel;
    private Long challongeParticipantId;
    private Instant createdAt = Instant.now();

    protected Registration() {
    }

    public Registration(Long tournamentId, String gamertag, String teamName, String teammates, String seatLabel) {
        this.tournamentId = tournamentId;
        this.gamertag = gamertag;
        this.teamName = teamName;
        this.teammates = teammates;
        this.seatLabel = seatLabel;
    }

    /** Name used on Challonge and in the participant list: the team for team tournaments, else the gamertag. */
    public String displayName() {
        return teamName != null && !teamName.isBlank() ? teamName : gamertag;
    }

    public Long getId() { return id; }
    public Long getTournamentId() { return tournamentId; }
    public String getGamertag() { return gamertag; }
    public String getTeamName() { return teamName; }
    public String getTeammates() { return teammates; }
    public String getSeatLabel() { return seatLabel; }
    public Long getChallongeParticipantId() { return challongeParticipantId; }
    public void setChallongeParticipantId(Long challongeParticipantId) { this.challongeParticipantId = challongeParticipantId; }
    public Instant getCreatedAt() { return createdAt; }
}
