package com.lanparty.dashboard.seating;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class SeatRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private String label;
    private int seatCount;
    private int sort;

    protected SeatRow() {
    }

    public SeatRow(Long eventId, String label, int seatCount, int sort) {
        this.eventId = eventId;
        this.label = label;
        this.seatCount = seatCount;
        this.sort = sort;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getSeatCount() { return seatCount; }
    public void setSeatCount(int seatCount) { this.seatCount = seatCount; }
    public int getSort() { return sort; }
    public void setSort(int sort) { this.sort = sort; }
}
