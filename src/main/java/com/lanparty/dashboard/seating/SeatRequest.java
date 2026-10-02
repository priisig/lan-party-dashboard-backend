package com.lanparty.dashboard.seating;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A participant's seat reservation that an orga confirms or rejects (when approval is required). */
@Entity
public class SeatRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long seatId;
    private String gamertag;
    private Long userId;
    private String companions;

    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.PENDING;

    private Instant createdAt = Instant.now();

    protected SeatRequest() {
    }

    public SeatRequest(Long seatId, String gamertag, Long userId, String companions) {
        this.seatId = seatId;
        this.gamertag = gamertag;
        this.userId = userId;
        this.companions = companions;
    }

    public Long getId() { return id; }
    public Long getSeatId() { return seatId; }
    public String getGamertag() { return gamertag; }
    public Long getUserId() { return userId; }
    public String getCompanions() { return companions; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
