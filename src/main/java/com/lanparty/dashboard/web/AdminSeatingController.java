package com.lanparty.dashboard.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.seating.SeatingDtos.AssignRequest;
import com.lanparty.dashboard.seating.SeatingDtos.LayoutRequest;
import com.lanparty.dashboard.seating.SeatingDtos.PendingRequest;
import com.lanparty.dashboard.seating.SeatingDtos.SeatMapView;
import com.lanparty.dashboard.seating.SeatingService;

@RestController
@RequestMapping("/api/admin/events/{eventId}/seats")
public class AdminSeatingController {

    private final AdminEvents adminEvents;
    private final SeatingService seating;

    public AdminSeatingController(AdminEvents adminEvents, SeatingService seating) {
        this.adminEvents = adminEvents;
        this.seating = seating;
    }

    @GetMapping
    public SeatMapView map(@PathVariable Long eventId) {
        return seating.map(adminEvents.get(eventId), true);
    }

    @PutMapping("/layout")
    public SeatMapView layout(@PathVariable Long eventId, @Valid @RequestBody LayoutRequest request) {
        Event event = adminEvents.get(eventId);
        seating.updateLayout(event, request);
        return seating.map(adminEvents.get(eventId), true);
    }

    @PutMapping("/{label}")
    public SeatMapView assign(@PathVariable Long eventId, @PathVariable String label, @Valid @RequestBody AssignRequest request) {
        Event event = adminEvents.get(eventId);
        seating.assign(event, label, request);
        return seating.map(event, true);
    }

    @PostMapping("/{label}/block")
    public SeatMapView block(@PathVariable Long eventId, @PathVariable String label) {
        Event event = adminEvents.get(eventId);
        seating.block(event, label);
        return seating.map(event, true);
    }

    @PostMapping("/{label}/release")
    public SeatMapView release(@PathVariable Long eventId, @PathVariable String label) {
        Event event = adminEvents.get(eventId);
        seating.release(event, label);
        return seating.map(event, true);
    }

    @GetMapping("/requests")
    public List<PendingRequest> pending(@PathVariable Long eventId) {
        return seating.pending(adminEvents.get(eventId));
    }

    @PostMapping("/requests/{requestId}/approve")
    public List<PendingRequest> approve(@PathVariable Long eventId, @PathVariable Long requestId) {
        Event event = adminEvents.get(eventId);
        seating.approve(event, requestId);
        return seating.pending(event);
    }

    @PostMapping("/requests/{requestId}/reject")
    public List<PendingRequest> reject(@PathVariable Long eventId, @PathVariable Long requestId) {
        Event event = adminEvents.get(eventId);
        seating.reject(event, requestId);
        return seating.pending(event);
    }
}
