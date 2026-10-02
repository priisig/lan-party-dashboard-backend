package com.lanparty.dashboard.seating;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private Long rowId;
    private int number;
    private String label;

    @Enumerated(EnumType.STRING)
    private SeatStatus status = SeatStatus.FREE;

    private String gamertag;
    private String note;

    protected Seat() {
    }

    public Seat(Long eventId, Long rowId, int number, String label) {
        this.eventId = eventId;
        this.rowId = rowId;
        this.number = number;
        this.label = label;
    }

    public void assign(String gamertag) {
        this.status = SeatStatus.TAKEN;
        this.gamertag = gamertag;
    }

    public void block() {
        this.status = SeatStatus.BLOCKED;
        this.gamertag = null;
    }

    public void release() {
        this.status = SeatStatus.FREE;
        this.gamertag = null;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getRowId() { return rowId; }
    public void setRowId(Long rowId) { this.rowId = rowId; }
    public int getNumber() { return number; }
    public String getLabel() { return label; }
    public SeatStatus getStatus() { return status; }
    public String getGamertag() { return gamertag; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
