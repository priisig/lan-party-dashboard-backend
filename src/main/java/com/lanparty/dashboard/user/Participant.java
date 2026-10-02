package com.lanparty.dashboard.user;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A user taking part in one event: payment and check-in are tracked per LAN. */
@Entity
@Table(name = "event_participant")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private Long userId;
    private boolean paid;
    private boolean checkedIn;
    private Instant createdAt = Instant.now();

    protected Participant() {
    }

    public Participant(Long eventId, Long userId) {
        this.eventId = eventId;
        this.userId = userId;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getUserId() { return userId; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public boolean isCheckedIn() { return checkedIn; }
    public void setCheckedIn(boolean checkedIn) { this.checkedIn = checkedIn; }
    public Instant getCreatedAt() { return createdAt; }
}
