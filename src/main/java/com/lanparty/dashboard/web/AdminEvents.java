package com.lanparty.dashboard.web;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.common.NotFoundException;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.event.EventRepository;

/** Resolves the event an admin request works on (admins can prepare next year's event before activating it). */
@Component
public class AdminEvents {

    private final EventRepository events;

    public AdminEvents(EventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Event get(Long id) {
        return events.findById(id).orElseThrow(() -> new NotFoundException("Event nicht gefunden."));
    }
}
