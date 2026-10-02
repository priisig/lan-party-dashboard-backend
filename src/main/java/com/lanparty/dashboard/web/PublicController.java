package com.lanparty.dashboard.web;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.lanparty.dashboard.announcement.Banner;
import com.lanparty.dashboard.announcement.BannerService;
import com.lanparty.dashboard.event.ActiveEventService;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.event.EventDtos.EventView;
import com.lanparty.dashboard.event.EventService;
import com.lanparty.dashboard.info.InfoService;
import com.lanparty.dashboard.info.InfoService.InfoDto;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.schedule.ScheduleService;
import com.lanparty.dashboard.schedule.ScheduleView;
import com.lanparty.dashboard.seating.SeatingDtos.ReservationRequest;
import com.lanparty.dashboard.seating.SeatingDtos.SeatMapView;
import com.lanparty.dashboard.seating.SeatingService;
import com.lanparty.dashboard.server.GameServerService;
import com.lanparty.dashboard.server.GameServerService.PublicServer;
import com.lanparty.dashboard.stats.StatsService;
import com.lanparty.dashboard.stats.StatsService.StatsView;
import com.lanparty.dashboard.challonge.ChallongeSyncService;
import com.lanparty.dashboard.tournament.TournamentDtos.Detail;
import com.lanparty.dashboard.tournament.TournamentDtos.RegistrationRequest;
import com.lanparty.dashboard.tournament.TournamentDtos.Summary;
import com.lanparty.dashboard.tournament.TournamentService;

/** Read-only dashboard data for the active event plus the two public forms (tournament sign-up, seat reservation). */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final ActiveEventService activeEvent;
    private final EventService events;
    private final InfoService infos;
    private final BannerService banners;
    private final LiveService live;
    private final ScheduleService schedule;
    private final GameServerService servers;
    private final TournamentService tournaments;
    private final ChallongeSyncService challonge;
    private final SeatingService seating;
    private final StatsService stats;
    private final ChangeNotifier notifier;
    private final Clock clock;

    public PublicController(ActiveEventService activeEvent, EventService events, InfoService infos, BannerService banners,
                            LiveService live, ScheduleService schedule, GameServerService servers,
                            TournamentService tournaments, ChallongeSyncService challonge, SeatingService seating,
                            StatsService stats, ChangeNotifier notifier, Clock clock) {
        this.activeEvent = activeEvent;
        this.events = events;
        this.infos = infos;
        this.banners = banners;
        this.live = live;
        this.schedule = schedule;
        this.servers = servers;
        this.tournaments = tournaments;
        this.challonge = challonge;
        this.seating = seating;
        this.stats = stats;
        this.notifier = notifier;
        this.clock = clock;
    }

    public record EventInfo(EventView event, List<InfoDto> infos, Instant serverTime) {
    }

    @GetMapping("/event")
    public EventInfo event() {
        Event event = activeEvent.get();
        return new EventInfo(EventView.of(event), infos.list(event), clock.instant());
    }

    @GetMapping("/time")
    public Instant time() {
        return clock.instant();
    }

    @GetMapping("/banners")
    public List<Banner> banners() {
        return banners.current(activeEvent.get());
    }

    @GetMapping("/live")
    public LiveService.LiveTag live() {
        return live.current(activeEvent.get());
    }

    @GetMapping("/schedule")
    public ScheduleView schedule() {
        return schedule.view(activeEvent.get());
    }

    @GetMapping("/servers")
    public List<PublicServer> servers() {
        return servers.publicList(activeEvent.get());
    }

    @GetMapping("/tournaments")
    public List<Summary> tournaments() {
        return tournaments.summaries(activeEvent.get());
    }

    @GetMapping("/tournaments/{id}")
    public Detail tournament(@PathVariable Long id) {
        return tournaments.detail(activeEvent.get(), id);
    }

    @PostMapping("/tournaments/{id}/registrations")
    public Detail register(@PathVariable Long id, @Valid @RequestBody RegistrationRequest request) {
        Event event = activeEvent.get();
        tournaments.register(event, id, request);
        challonge.syncSoon(id);
        return tournaments.detail(event, id);
    }

    @GetMapping("/seats")
    public SeatMapView seats() {
        return seating.map(activeEvent.get(), false);
    }

    @PostMapping("/seats/{label}/requests")
    public SeatMapView requestSeat(@PathVariable String label, @Valid @RequestBody ReservationRequest request) {
        Event event = activeEvent.get();
        seating.requestSeat(event, label, request);
        return seating.map(event, false);
    }

    @GetMapping("/stats")
    public StatsView stats() {
        return stats.view(activeEvent.get());
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return notifier.subscribe();
    }

    @GetMapping("/events/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable Long id) {
        EventService.Logo logo = events.logo(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS))
                .body(logo.data());
    }
}
