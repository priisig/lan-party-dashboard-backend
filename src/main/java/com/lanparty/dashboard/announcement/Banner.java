package com.lanparty.dashboard.announcement;

import java.time.Instant;

/**
 * One message in the announcement band.
 *
 * @param countdownTo when set, the frontend shows a live countdown to this instant
 */
public record Banner(String id, Kind kind, String text, Instant countdownTo) {

    public enum Kind {
        MANUAL, REGISTRATION_CLOSING
    }
}
