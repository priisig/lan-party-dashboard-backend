package com.lanparty.dashboard.web;

import java.io.IOException;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lanparty.dashboard.announcement.AnnouncementService;
import com.lanparty.dashboard.announcement.AnnouncementService.AnnouncementDto;
import com.lanparty.dashboard.event.EventDtos.CreateEventRequest;
import com.lanparty.dashboard.event.EventDtos.EventRequest;
import com.lanparty.dashboard.event.EventDtos.EventView;
import com.lanparty.dashboard.event.EventService;
import com.lanparty.dashboard.info.InfoService;
import com.lanparty.dashboard.info.InfoService.InfoDto;
import com.lanparty.dashboard.schedule.ScheduleAdminService;
import com.lanparty.dashboard.schedule.ScheduleAdminService.ScheduleDto;
import com.lanparty.dashboard.server.GameServerService;
import com.lanparty.dashboard.server.GameServerService.ServerDto;

/** Event lifecycle and the simple list-style content (infos, announcements, servers, schedule). */
@RestController
@RequestMapping("/api/admin/events")
public class AdminEventController {

    private final EventService events;
    private final AdminEvents adminEvents;
    private final InfoService infos;
    private final AnnouncementService announcements;
    private final GameServerService servers;
    private final ScheduleAdminService schedule;

    public AdminEventController(EventService events, AdminEvents adminEvents, InfoService infos,
                                AnnouncementService announcements, GameServerService servers, ScheduleAdminService schedule) {
        this.events = events;
        this.adminEvents = adminEvents;
        this.infos = infos;
        this.announcements = announcements;
        this.servers = servers;
        this.schedule = schedule;
    }

    @GetMapping
    public List<EventView> list() {
        return events.list();
    }

    @PostMapping
    public EventView create(@Valid @RequestBody CreateEventRequest request) {
        return events.create(request);
    }

    @GetMapping("/{id}")
    public EventView get(@PathVariable Long id) {
        return EventView.of(adminEvents.get(id));
    }

    @PutMapping("/{id}")
    public EventView update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
        return events.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public EventView activate(@PathVariable Long id) {
        return events.activate(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        events.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/logo")
    public EventView uploadLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        return events.setLogo(id, file.getBytes(), file.getContentType());
    }

    @DeleteMapping("/{id}/logo")
    public EventView removeLogo(@PathVariable Long id) {
        return events.removeLogo(id);
    }

    @GetMapping("/{id}/infos")
    public List<InfoDto> infos(@PathVariable Long id) {
        return infos.list(adminEvents.get(id));
    }

    @PutMapping("/{id}/infos")
    public List<InfoDto> replaceInfos(@PathVariable Long id, @Valid @RequestBody List<@Valid InfoDto> items) {
        return infos.replace(adminEvents.get(id), items);
    }

    @GetMapping("/{id}/announcements")
    public List<AnnouncementDto> announcements(@PathVariable Long id) {
        return announcements.list(adminEvents.get(id));
    }

    @PutMapping("/{id}/announcements")
    public List<AnnouncementDto> replaceAnnouncements(@PathVariable Long id, @Valid @RequestBody List<@Valid AnnouncementDto> items) {
        return announcements.replace(adminEvents.get(id), items);
    }

    @GetMapping("/{id}/servers")
    public List<ServerDto> servers(@PathVariable Long id) {
        return servers.list(adminEvents.get(id));
    }

    @PutMapping("/{id}/servers")
    public List<ServerDto> replaceServers(@PathVariable Long id, @Valid @RequestBody List<@Valid ServerDto> items) {
        return servers.replace(adminEvents.get(id), items);
    }

    @GetMapping("/{id}/schedule")
    public List<ScheduleDto> schedule(@PathVariable Long id) {
        return schedule.list(adminEvents.get(id));
    }

    @PutMapping("/{id}/schedule")
    public List<ScheduleDto> replaceSchedule(@PathVariable Long id, @Valid @RequestBody List<@Valid ScheduleDto> items) {
        return schedule.replace(adminEvents.get(id), items);
    }
}
