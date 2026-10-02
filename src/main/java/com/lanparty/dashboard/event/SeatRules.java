package com.lanparty.dashboard.event;

import jakarta.persistence.Embeddable;

/** What participants may do themselves on the seat map. */
@Embeddable
public class SeatRules {

    /** New reservations possible; when closed only orgas assign seats. */
    private boolean seatSelectionOpen = true;
    /** Participants with a seat may move to another free one. */
    private boolean seatChangeAllowed = true;
    /** Reservations are requests that an orga approves instead of instant bookings. */
    private boolean seatApprovalRequired;
    /** "Gut zu wissen" text next to the seat map (table size, power, LAN port…). */
    private String seatInfo;

    public boolean isSeatSelectionOpen() { return seatSelectionOpen; }
    public void setSeatSelectionOpen(boolean seatSelectionOpen) { this.seatSelectionOpen = seatSelectionOpen; }
    public boolean isSeatChangeAllowed() { return seatChangeAllowed; }
    public void setSeatChangeAllowed(boolean seatChangeAllowed) { this.seatChangeAllowed = seatChangeAllowed; }
    public boolean isSeatApprovalRequired() { return seatApprovalRequired; }
    public void setSeatApprovalRequired(boolean seatApprovalRequired) { this.seatApprovalRequired = seatApprovalRequired; }
    public String getSeatInfo() { return seatInfo; }
    public void setSeatInfo(String seatInfo) { this.seatInfo = seatInfo; }
}
