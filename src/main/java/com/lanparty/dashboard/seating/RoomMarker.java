package com.lanparty.dashboard.seating;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A landmark around the seat map: beamer/stage, entrance, kitchen… */
@Entity
public class RoomMarker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;

    @Enumerated(EnumType.STRING)
    private MarkerKind kind;

    private String label;

    @Enumerated(EnumType.STRING)
    private RoomSide side;

    @Enumerated(EnumType.STRING)
    private MarkerAlign align;

    private int sort;

    protected RoomMarker() {
    }

    public RoomMarker(Long eventId, MarkerKind kind, String label, RoomSide side, MarkerAlign align, int sort) {
        this.eventId = eventId;
        this.kind = kind;
        this.label = label;
        this.side = side;
        this.align = align;
        this.sort = sort;
    }

    public RoomMarker copyTo(Long otherEventId) {
        return new RoomMarker(otherEventId, kind, label, side, align, sort);
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public MarkerKind getKind() { return kind; }
    public String getLabel() { return label; }
    public RoomSide getSide() { return side; }
    public MarkerAlign getAlign() { return align; }
    public int getSort() { return sort; }
}
