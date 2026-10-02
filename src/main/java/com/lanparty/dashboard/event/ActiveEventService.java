package com.lanparty.dashboard.event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.common.NotFoundException;

@Service
public class ActiveEventService {

    private final EventRepository events;

    public ActiveEventService(EventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Event get() {
        return events.findFirstByActiveTrue()
                .orElseThrow(() -> new NotFoundException("Kein aktiver Event – bitte im Admin-Bereich einen Event aktivieren."));
    }

    @Transactional(readOnly = true)
    public Long id() {
        return get().getId();
    }
}
