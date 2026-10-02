package com.lanparty.dashboard.event;

import java.time.Duration;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.common.BadRequestException;
import com.lanparty.dashboard.common.NotFoundException;
import com.lanparty.dashboard.event.EventDtos.CreateEventRequest;
import com.lanparty.dashboard.event.EventDtos.EventRequest;
import com.lanparty.dashboard.event.EventDtos.EventView;
import com.lanparty.dashboard.event.EventDtos.NetworkDto;
import com.lanparty.dashboard.event.EventDtos.SeatRulesDto;
import com.lanparty.dashboard.common.Json;
import com.lanparty.dashboard.info.InfoItem;
import com.lanparty.dashboard.info.InfoItemRepository;
import com.lanparty.dashboard.realtime.ChangeNotifier;
import com.lanparty.dashboard.realtime.Topic;
import com.lanparty.dashboard.schedule.ScheduleItem;
import com.lanparty.dashboard.schedule.ScheduleItemRepository;
import com.lanparty.dashboard.seating.Seat;
import com.lanparty.dashboard.seating.SeatRepository;
import com.lanparty.dashboard.seating.SeatRowRepository;
import com.lanparty.dashboard.seating.SeatStatus;
import com.lanparty.dashboard.seating.SeatingService;
import com.lanparty.dashboard.server.GameServer;
import com.lanparty.dashboard.server.GameServerRepository;
import com.lanparty.dashboard.stats.Integration;
import com.lanparty.dashboard.stats.IntegrationRepository;
import com.lanparty.dashboard.tournament.Tournament;
import com.lanparty.dashboard.tournament.TournamentRepository;

@Service
public class EventService {

    static final long MAX_LOGO_BYTES = 2 * 1024 * 1024;
    static final Set<String> LOGO_TYPES = Set.of("image/png", "image/jpeg", "image/svg+xml", "image/webp", "image/gif");

    private final EventRepository events;
    private final InfoItemRepository infos;
    private final GameServerRepository servers;
    private final TournamentRepository tournaments;
    private final ScheduleItemRepository schedule;
    private final SeatRowRepository seatRows;
    private final SeatRepository seats;
    private final SeatingService seating;
    private final IntegrationRepository integrations;
    private final ChangeNotifier notifier;

    public EventService(EventRepository events, InfoItemRepository infos, GameServerRepository servers,
                        TournamentRepository tournaments, ScheduleItemRepository schedule, SeatRowRepository seatRows,
                        SeatRepository seats, SeatingService seating, IntegrationRepository integrations,
                        ChangeNotifier notifier) {
        this.events = events;
        this.infos = infos;
        this.servers = servers;
        this.tournaments = tournaments;
        this.schedule = schedule;
        this.seatRows = seatRows;
        this.seats = seats;
        this.seating = seating;
        this.integrations = integrations;
        this.notifier = notifier;
    }

    @Transactional(readOnly = true)
    public List<EventView> list() {
        return events.findAllByOrderByStartsAtDesc().stream().map(EventView::of).toList();
    }

    @Transactional
    public EventView update(Long id, EventRequest r) {
        Event e = find(id);
        validateRange(r.startsAt(), r.endsAt());
        e.setTitle(r.title().trim());
        e.setSubtitle(blankToNull(r.subtitle()));
        e.setLocation(blankToNull(r.location()));
        if (r.timezone() != null && !r.timezone().isBlank()) {
            try {
                ZoneId.of(r.timezone());
            } catch (Exception ex) {
                throw new BadRequestException("Unbekannte Zeitzone: " + r.timezone());
            }
            e.setTimezone(r.timezone());
        }
        e.setStartsAt(r.startsAt());
        e.setEndsAt(r.endsAt());
        e.setWelcomeTitle(blankToNull(r.welcomeTitle()));
        e.setWelcomeText(blankToNull(r.welcomeText()));
        e.setKioskIntervalSec(r.kioskIntervalSec());
        if (r.kioskViews() != null && !r.kioskViews().isBlank()) {
            e.setKioskViews(r.kioskViews());
        }
        e.setLoginHeadline(blankToNull(r.loginHeadline()));
        if (r.headings() != null) {
            var node = Json.object();
            r.headings().forEach((key, value) -> {
                if (!EventDtos.HEADING_KEYS.contains(key)) {
                    throw new BadRequestException("Unbekannte Überschrift: " + key);
                }
                if (value != null && !value.isBlank()) {
                    node.put(key, value.trim());
                }
            });
            e.setHeadings(Json.write(node));
        }
        notifier.publish(Topic.EVENT);
        notifier.publish(Topic.SCHEDULE);
        return EventView.of(e);
    }

    @Transactional
    public EventView updateNetwork(Long id, NetworkDto r) {
        Event e = find(id);
        NetworkInfo n = e.getNetwork();
        n.setWifiSsid(blankToNull(r.wifiSsid()));
        n.setWifiPassword(r.wifiPassword() == null || r.wifiPassword().isEmpty() ? null : r.wifiPassword());
        n.setWifiSecurity(r.wifiSecurity());
        n.setWifiHidden(r.wifiHidden());
        n.setLanIpMode(blankToNull(r.lanIpMode()));
        n.setLanSubnet(blankToNull(r.lanSubnet()));
        n.setLanGateway(blankToNull(r.lanGateway()));
        n.setTsAddress(blankToNull(r.tsAddress()));
        n.setTsPort(r.tsPort());
        n.setTsPassword(blankToNull(r.tsPassword()));
        if (n.getWifiSecurity() != WifiSecurity.OPEN && n.getWifiSsid() != null
                && (n.getWifiPassword() == null || n.getWifiPassword().length() < 8)) {
            throw new BadRequestException("Ein WPA-Passwort braucht mindestens 8 Zeichen.");
        }
        notifier.publish(Topic.EVENT);
        return EventView.of(e);
    }

    @Transactional
    public EventView updateSeatRules(Long id, SeatRulesDto r) {
        Event e = find(id);
        SeatRules rules = e.getSeatRules();
        rules.setSeatSelectionOpen(r.selectionOpen());
        rules.setSeatChangeAllowed(r.changeAllowed());
        rules.setSeatApprovalRequired(r.approvalRequired());
        rules.setSeatInfo(blankToNull(r.info()));
        notifier.publish(Topic.EVENT);
        notifier.publish(Topic.SEATS);
        return EventView.of(e);
    }

    @Transactional
    public EventView create(CreateEventRequest r) {
        validateRange(r.startsAt(), r.endsAt());
        if (events.existsBySlug(r.slug())) {
            throw new BadRequestException("Slug «" + r.slug() + "» ist schon vergeben.");
        }
        Event e = new Event();
        e.setTitle(r.title().trim());
        e.setSlug(r.slug());
        e.setStartsAt(r.startsAt());
        e.setEndsAt(r.endsAt());
        if (r.cloneFromId() != null) {
            Event source = find(r.cloneFromId());
            copySettings(source, e);
            events.save(e);
            cloneContent(source, e);
        } else {
            e.setWelcomeTitle("Schön bist du da!");
            events.save(e);
            seating.createLayout(e.getId(), List.of(
                    new com.lanparty.dashboard.seating.SeatRow(e.getId(), "A", 10, 0),
                    new com.lanparty.dashboard.seating.SeatRow(e.getId(), "B", 10, 1)), Set.of());
        }
        return EventView.of(e);
    }

    @Transactional
    public EventView activate(Long id) {
        Event e = find(id);
        events.deactivateAll();
        events.flush();
        e = find(id);
        e.setActive(true);
        notifier.publish(Topic.EVENT);
        for (Topic t : Topic.values()) {
            notifier.publish(t);
        }
        return EventView.of(e);
    }

    @Transactional
    public void delete(Long id) {
        Event e = find(id);
        if (e.isActive()) {
            throw new BadRequestException("Der aktive Event kann nicht gelöscht werden.");
        }
        events.delete(e);
    }

    @Transactional
    public EventView setLogo(Long id, byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            throw new BadRequestException("Leere Datei.");
        }
        if (data.length > MAX_LOGO_BYTES) {
            throw new BadRequestException("Logo ist grösser als 2 MB.");
        }
        if (contentType == null || !LOGO_TYPES.contains(contentType)) {
            throw new BadRequestException("Erlaubt sind PNG, JPEG, SVG, WebP oder GIF.");
        }
        Event e = find(id);
        e.setLogo(data);
        e.setLogoContentType(contentType);
        notifier.publish(Topic.EVENT);
        return EventView.of(e);
    }

    @Transactional
    public EventView removeLogo(Long id) {
        Event e = find(id);
        e.setLogo(null);
        e.setLogoContentType(null);
        notifier.publish(Topic.EVENT);
        return EventView.of(e);
    }

    public record Logo(byte[] data, String contentType) {
    }

    @Transactional(readOnly = true)
    public Logo logo(Long id) {
        Event e = find(id);
        if (e.getLogo() == null) {
            throw new NotFoundException("Kein Logo.");
        }
        return new Logo(e.getLogo(), e.getLogoContentType());
    }

    private void copySettings(Event source, Event target) {
        target.setSubtitle(source.getSubtitle());
        target.setLocation(source.getLocation());
        target.setTimezone(source.getTimezone());
        target.setWelcomeTitle(source.getWelcomeTitle());
        target.setWelcomeText(source.getWelcomeText());
        target.setLogo(source.getLogo());
        target.setLogoContentType(source.getLogoContentType());
        target.setKioskIntervalSec(source.getKioskIntervalSec());
        target.setLoginHeadline(source.getLoginHeadline());
        target.setHeadings(source.getHeadings());
        NetworkInfo from = source.getNetwork();
        NetworkInfo to = target.getNetwork();
        to.setWifiSsid(from.getWifiSsid());
        to.setWifiPassword(from.getWifiPassword());
        to.setWifiSecurity(from.getWifiSecurity());
        to.setWifiHidden(from.isWifiHidden());
        to.setLanIpMode(from.getLanIpMode());
        to.setLanSubnet(from.getLanSubnet());
        to.setLanGateway(from.getLanGateway());
        to.setTsAddress(from.getTsAddress());
        to.setTsPort(from.getTsPort());
        to.setTsPassword(from.getTsPassword());
        SeatRules rules = target.getSeatRules();
        rules.setSeatSelectionOpen(source.getSeatRules().isSeatSelectionOpen());
        rules.setSeatChangeAllowed(source.getSeatRules().isSeatChangeAllowed());
        rules.setSeatApprovalRequired(source.getSeatRules().isSeatApprovalRequired());
        rules.setSeatInfo(source.getSeatRules().getSeatInfo());
        target.setKioskViews(source.getKioskViews());
    }

    /**
     * Copies everything that is "setup" rather than "what happened": infos, servers, tournaments (without
     * registrations / Challonge link), integrations, seat layout (orga seats stay blocked) and the schedule,
     * shifted by the difference between the two event start dates.
     */
    private void cloneContent(Event source, Event target) {
        Long from = source.getId();
        Long to = target.getId();
        infos.findByEventIdOrderBySort(from).forEach(i -> infos.save(new InfoItem(to, i.getLabel(), i.getValue(), i.getSort())));

        Map<Long, Long> serverIds = new HashMap<>();
        for (GameServer s : servers.findByEventIdOrderBySort(from)) {
            serverIds.put(s.getId(), servers.save(s.copyTo(to)).getId());
        }
        Duration shift = Duration.between(source.getStartsAt(), target.getStartsAt());
        Map<Long, Long> tournamentIds = new HashMap<>();
        for (Tournament t : tournaments.findByEventIdOrderBySort(from)) {
            Tournament copy = t.copyTo(to);
            copy.setServerId(t.getServerId() == null ? null : serverIds.get(t.getServerId()));
            copy.setStartsAt(t.getStartsAt() == null ? null : t.getStartsAt().plus(shift));
            tournamentIds.put(t.getId(), tournaments.save(copy).getId());
        }
        for (ScheduleItem item : schedule.findByEventIdOrderByStartsAt(from)) {
            schedule.save(new ScheduleItem(to, item.getStartsAt().plus(shift),
                    item.getEndsAt() == null ? null : item.getEndsAt().plus(shift), item.getTitle(), item.getLocation(),
                    item.getColor(), item.getTournamentId() == null ? null : tournamentIds.get(item.getTournamentId())));
        }
        integrations.findByEventIdOrderBySort(from).forEach(i -> integrations.save(i.copyTo(to)));

        Set<String> blocked = seats.findByEventId(from).stream()
                .filter(s -> s.getStatus() == SeatStatus.BLOCKED)
                .map(Seat::getLabel)
                .collect(Collectors.toSet());
        seating.createLayout(to, seatRows.findByEventIdOrderBySort(from), blocked);
        seating.copyRoom(source, target);
    }

    private Event find(Long id) {
        return events.findById(id).orElseThrow(() -> new NotFoundException("Event nicht gefunden."));
    }

    private static void validateRange(java.time.Instant start, java.time.Instant end) {
        if (!end.isAfter(start)) {
            throw new BadRequestException("Das Ende muss nach dem Start liegen.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
