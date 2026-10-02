package com.lanparty.dashboard.common;

/** Raised when a third-party system (Challonge, Uptime Kuma, a game server…) cannot be reached. */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
