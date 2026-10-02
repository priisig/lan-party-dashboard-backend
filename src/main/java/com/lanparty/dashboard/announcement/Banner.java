package com.lanparty.dashboard.announcement;

import java.time.Instant;

/**
 * One message in the announcement band. {@code text} may contain accent markup.
 *
 * @param countdownTo when set, the frontend shows a live countdown to this instant
 */
public record Banner(String id, Kind kind, Tone tone, String text, Instant countdownTo) {

    public enum Kind {
        MANUAL, REGISTRATION_CLOSING
    }

    /** Visual style of the banner: LIVE (green tag), INFO (blue), WARNING (orange). */
    public enum Tone {
        LIVE, INFO, WARNING
    }
}
