package com.lanparty.dashboard.common;

/** A user-facing validation error; the message is shown in the UI as-is (German). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
