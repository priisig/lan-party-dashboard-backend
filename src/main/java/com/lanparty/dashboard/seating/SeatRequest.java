package com.lanparty.dashboard.seating;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A public seat reservation that an admin confirms or rejects. */
@Entity
public class SeatRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long seatId;
    private String gamertag;
    private String companions;

    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.PENDING;

    private Instant createdAt = Instant.now();

    protected SeatRequest() {
    }

    public SeatRequest(Long seatId, String gamertag, String companions) {
        this.seatId = seatId;
        this.gamertag = gamertag;
        this.companions = companions;
    }

    public Long getId() { return id; }
    public Long getSeatId() { return seatId; }
    public String getGamertag() { return gamertag; }
    public String getCompanions() { return companions; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
