package com.lanparty.dashboard.demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.admin.Settings;
import com.lanparty.dashboard.announcement.Announcement;
import com.lanparty.dashboard.announcement.AnnouncementRepository;
import com.lanparty.dashboard.config.LanProperties;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.event.EventRepository;
import com.lanparty.dashboard.info.InfoItem;
import com.lanparty.dashboard.info.InfoItemRepository;
import com.lanparty.dashboard.schedule.ScheduleItem;
import com.lanparty.dashboard.schedule.ScheduleItemRepository;
import com.lanparty.dashboard.seating.MarkerAlign;
import com.lanparty.dashboard.seating.MarkerKind;
import com.lanparty.dashboard.seating.RoomMarker;
import com.lanparty.dashboard.seating.RoomMarkerRepository;
import com.lanparty.dashboard.seating.RoomSide;
import com.lanparty.dashboard.seating.Seat;
import com.lanparty.dashboard.seating.SeatOrientation;
import com.lanparty.dashboard.seating.SeatRepository;
import com.lanparty.dashboard.seating.SeatRequest;
import com.lanparty.dashboard.seating.SeatRequestRepository;
import com.lanparty.dashboard.seating.SeatRow;
import com.lanparty.dashboard.seating.SeatingService;
import com.lanparty.dashboard.server.GameServer;
import com.lanparty.dashboard.server.GameServerRepository;
import com.lanparty.dashboard.server.QueryType;
import com.lanparty.dashboard.stats.Integration;
import com.lanparty.dashboard.stats.IntegrationRepository;
import com.lanparty.dashboard.stats.StatsService;
import com.lanparty.dashboard.stats.StatsService.MetricPush;
import com.lanparty.dashboard.tournament.Registration;
import com.lanparty.dashboard.tournament.RegistrationRepository;
import com.lanparty.dashboard.tournament.Tournament;
import com.lanparty.dashboard.tournament.TournamentRepository;

/**
 * Seeds a demo event that mirrors the design prototype (LAN_SEED_DEMO=true, only when no event exists).
 * Times are relative to "now" so a tournament is live, one is about to close registration, etc.
 */
@Component
@Order(2)
public class DemoSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeed.class);
    private static final String GREY = "#6B6390";
    private static final String BLUE = "#3D8BFF";
    private static final String PURPLE = "#9B5CFF";
    private static final String GREEN = "#22D37A";
    private static final String CYAN = "#4FD1E8";

    private final LanProperties properties;
    private final EventRepository events;
    private final InfoItemRepository infos;
    private final AnnouncementRepository announcements;
    private final GameServerRepository servers;
    private final TournamentRepository tournaments;
    private final RegistrationRepository registrations;
    private final ScheduleItemRepository schedule;
    private final SeatingService seating;
    private final SeatRepository seats;
    private final SeatRequestRepository seatRequests;
    private final RoomMarkerRepository markers;
    private final IntegrationRepository integrations;
    private final StatsService stats;
    private final Settings settings;
    private final Clock clock;

    public DemoSeed(LanProperties properties, EventRepository events, InfoItemRepository infos,
                    AnnouncementRepository announcements, GameServerRepository servers, TournamentRepository tournaments,
                    RegistrationRepository registrations, ScheduleItemRepository schedule, SeatingService seating,
                    SeatRepository seats, SeatRequestRepository seatRequests, RoomMarkerRepository markers,
                    IntegrationRepository integrations,
                    StatsService stats, Settings settings, Clock clock) {
        this.properties = properties;
        this.events = events;
        this.infos = infos;
        this.announcements = announcements;
        this.servers = servers;
        this.tournaments = tournaments;
        this.registrations = registrations;
        this.schedule = schedule;
        this.seating = seating;
        this.seats = seats;
        this.seatRequests = seatRequests;
        this.markers = markers;
        this.integrations = integrations;
        this.stats = stats;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!properties.seedDemo() || events.count() > 0) {
            return;
        }
        ZoneId zone = ZoneId.of("Europe/Zurich");
        Instant now = clock.instant();
        // The CS2 KO round started ~70 minutes ago (rounded to 10 min); everything else is placed around it.
        ZonedDateTime ko = now.atZone(zone).minusMinutes(70).truncatedTo(ChronoUnit.HOURS)
                .plusMinutes((now.atZone(zone).minusMinutes(70).getMinute() / 10) * 10L);
        Instant k = ko.toInstant();

        Event e = new Event();
        e.setSlug("vivo-lan-2026");
        e.setTitle("VIVO LAN 2026");
        e.setSubtitle("[DATUM] · [ORT]");
        e.setTimezone(zone.getId());
        e.setStartsAt(k.minus(Duration.ofHours(24)));
        e.setEndsAt(k.plus(Duration.ofHours(24)));
        e.setActive(true);
        e.setWelcomeText("Alle Server, Turniere und den Zeitplan findest du hier. Für Turniere meldest du dich direkt im Tab «Turniere» an. Fragen? Das Orga-Team sitzt bei Platz A1.");
        e.setSeatOrientation(SeatOrientation.COLUMNS);
        e.setWelcomeTitle("Willkommen an der {lila:VIVO LAN}.");
        e.setLoginHeadline("Platz sichern.\n{lila:Rechner} {blau:einstecken.} {gruen:Zocken.}");
        e.getNetwork().setWifiSsid("VIVO-LAN");
        e.getNetwork().setWifiPassword("[PASSWORT]");
        e.getNetwork().setLanIpMode("DHCP (automatisch)");
        e.getNetwork().setLanSubnet("10.10.0.0/16");
        e.getNetwork().setLanGateway("10.10.0.1");
        e.getNetwork().setTsAddress("ts.vivolan.local");
        e.getNetwork().setTsPort(9987);
        e.getSeatRules().setSeatApprovalRequired(true);
        e.getSeatRules().setSeatInfo("Pro Platz: [TISCHBREITE] cm Tischfläche, 1 Steckdose (Mehrfachstecker mitbringen) und 1 LAN-Port.");
        e.setLogo(resource("demo/logo.jpg"));
        e.setLogoContentType("image/jpeg");
        events.save(e);
        Long id = e.getId();

        List<String[]> infoRows = List.of(
                new String[] {"WLAN", "VIVO-LAN · [PASSWORT]"},
                new String[] {"Voice", "ts.vivolan.local"},
                new String[] {"Essen & Getränke", "Theke offen [ZEITEN] · Abendessen 19:00"},
                new String[] {"Orga & Notfall", "[NAME] · Platz A1 · [TELEFON]"},
                new String[] {"Hausregeln", "Ruhezone Schlafraum ab [ZEIT] · Kein Essen an den Plätzen mit Strom-Mehrfachstecker"});
        for (int i = 0; i < infoRows.size(); i++) {
            infos.save(new InfoItem(id, infoRows.get(i)[0], infoRows.get(i)[1], i));
        }
        announcements.save(new Announcement(id, "{gruen:Pizza}-Bestellung bis 18:30 an der Theke", com.lanparty.dashboard.announcement.Banner.Tone.INFO, true, null, k.plus(Duration.ofHours(2)), 0));

        GameServer cs1 = server(id, "CS2 · Turnier 5v5", "CS2-1", "10.0.0.21", 27015, QueryType.SOURCE, null, 0);
        server(id, "CS2 · Deathmatch", "CS2-2", "10.0.0.22", 27016, QueryType.SOURCE, null, 1);
        GameServer cod = server(id, "CoD4 · Promod", "COD-1", "10.0.0.23", 28960, QueryType.QUAKE3, null, 2);
        GameServer mc = server(id, "Minecraft · Survival", "MC-1", "mc.vivolan.local", null, QueryType.MINECRAFT, null, 3);
        GameServer gmod = server(id, "Garry’s Mod · Prop Hunt", "GMOD-1", "10.0.0.24", 27017, QueryType.SOURCE, k.plus(Duration.ofMinutes(330)), 4);

        Tournament cs2 = tournament(id, "CS2 5v5", BLUE, "Single Elimination · 8 Teams", 8, 5, false, null,
                k.minus(Duration.ofMinutes(150)), cs1.getId(), 0);
        cs2.setChallongeSnapshot(new String(resource("demo/cs2-bracket.json"), StandardCharsets.UTF_8));
        cs2.setSnapshotAt(now);
        Tournament cod4 = tournament(id, "CoD4 2v2", PURPLE, "Double Elimination · 16 Teams", 16, 2, true,
                now.plus(Duration.ofMinutes(25)).truncatedTo(ChronoUnit.MINUTES), k.plus(Duration.ofMinutes(210)), cod.getId(), 1);
        Tournament ph = tournament(id, "GMod Prop Hunt", CYAN, "Punkte-Modus · bis 24 Spieler", 24, 1, true, null,
                k.plus(Duration.ofMinutes(330)), gmod.getId(), 2);
        tournament(id, "Minecraft Speedrun", GREEN, "Zeitrangliste · bis 20 Spieler", 20, 1, false, null,
                k.plus(Duration.ofHours(18)).plus(Duration.ofMinutes(30)), mc.getId(), 3);

        List<String> teams = List.of("Lag Legends", "Noob Squad", "Pixel Panzer", "Headshot Hamster", "Rush B",
                "No Scope Nerds", "Camper Club", "Ping 999", "Clutch Kings", "Respawn Rats", "Frag Freunde",
                "AFK Alpakas", "Team 13", "Team 14");
        for (int i = 0; i < teams.size(); i++) {
            registrations.save(new Registration(cod4.getId(), "player" + (i + 1), teams.get(i), null, null));
        }
        List<String> hunters = List.of("Lag_Legend", "PixelPanzer", "Ping999", "RushB", "AFK_Alpaka", "ClutchKing",
                "Creeper_Kai", "Diamant_Dani", "Redstone_Rob");
        hunters.forEach(h -> registrations.save(new Registration(ph.getId(), h, null, null, null)));

        item(id, k.minus(Duration.ofMinutes(450)), "Frühstück", "Theke", GREY, null);
        item(id, k.minus(Duration.ofMinutes(330)), "Minecraft Bau-Challenge", "MC-1", GREEN, null);
        item(id, k.minus(Duration.ofMinutes(150)), "CS2 Turnier · Gruppenphase", "CS2-1 / CS2-2", BLUE, cs2.getId());
        item(id, k, "CS2 Turnier · KO-Runde", "CS2-1", BLUE, cs2.getId());
        item(id, k.plus(Duration.ofMinutes(150)), "Abendessen", "Theke", GREY, null);
        item(id, k.plus(Duration.ofMinutes(210)), "CoD4 Turnier · 2v2", "COD-1", PURPLE, cod4.getId());
        item(id, k.plus(Duration.ofMinutes(330)), "Garry’s Mod · Prop Hunt", "GMOD-1", CYAN, ph.getId());
        item(id, k.plus(Duration.ofMinutes(450)), "Mitternachts-Snack", "Theke", GREY, null);
        item(id, k.plus(Duration.ofMinutes(510)), "Free Play", "alle Server", GREY, null);

        markers.save(new RoomMarker(id, MarkerKind.BEAMER, "Bühne · Beamer", RoomSide.LEFT, MarkerAlign.CENTER, 0));
        markers.save(new RoomMarker(id, MarkerKind.ENTRANCE, "Eingang", RoomSide.TOP, MarkerAlign.END, 1));
        markers.save(new RoomMarker(id, MarkerKind.OTHER, "Theke", RoomSide.BOTTOM, MarkerAlign.END, 2));
        seating.createLayout(id, List.of(new SeatRow(id, "A", 10, 0), new SeatRow(id, "B", 10, 1)), Set.of("A1", "A2"));
        String[] seated = {"Lag_L", "Pixel", "Ping9", "RushB", "AFK_A", "Clutch", "Kai", "Dani", "Rob", "Nina", "Ben", "Ella", "Max", "Vic"};
        List<String> takenLabels = List.of("A3", "A4", "A5", "A7", "A8", "A10", "B1", "B2", "B3", "B5", "B6", "B8", "B9", "B10");
        for (int i = 0; i < takenLabels.size(); i++) {
            Seat seat = seats.findByEventIdAndLabelIgnoreCase(id, takenLabels.get(i)).orElseThrow();
            seat.assign(seated[i]);
        }
        Seat requested = seats.findByEventIdAndLabelIgnoreCase(id, "B4").orElseThrow();
        seatRequests.save(new SeatRequest(requested.getId(), "NoScopeNina", "Rush B"));

        Integration kuma = new Integration(id, "uptime-kuma", "Service-Status · Uptime Kuma",
                "{\"baseUrl\":\"http://status.vivolan.local:3001\",\"slug\":\"lan\"}", 0);
        kuma.setLastResult(new String(resource("demo/uptime-kuma.json"), StandardCharsets.UTF_8));
        kuma.setLastOkAt(now);
        integrations.save(kuma);
        Integration minecraft = new Integration(id, "minecraft", "Minecraft · Survival", "{\"host\":\"mc.vivolan.local\"}", 1);
        minecraft.setLastResult(new String(resource("demo/minecraft.json"), StandardCharsets.UTF_8));
        minecraft.setLastOkAt(now);
        integrations.save(minecraft);

        stats.push(e, List.of(
                new MetricPush("clients", "LAN-Clients online", "87", null, 0),
                new MetricPush("internet", "Internet ↓ / ↑", "742", "/ 98 Mbit/s", 1),
                new MetricPush("traffic", "Traffic seit Start", "3.4", "TB", 2)));
        settings.pushToken();
        log.info("Seeded demo event '{}'.", e.getTitle());
    }

    private GameServer server(Long eventId, String name, String code, String host, Integer port, QueryType type, Instant from, int sort) {
        GameServer s = new GameServer();
        s.setEventId(eventId);
        s.setName(name);
        s.setShortCode(code);
        s.setHost(host);
        s.setPort(port);
        s.setQueryType(type);
        s.setAvailableFrom(from);
        s.setSort(sort);
        return servers.save(s);
    }

    private Tournament tournament(Long eventId, String name, String color, String format, int max, int teamSize,
                                  boolean open, Instant closesAt, Instant startsAt, Long serverId, int sort) {
        Tournament t = new Tournament();
        t.setEventId(eventId);
        t.setName(name);
        t.setColor(color);
        t.setFormatLabel(format);
        t.setMaxParticipants(max);
        t.setTeamSize(teamSize);
        t.setRegistrationOpen(open);
        t.setRegistrationClosesAt(closesAt);
        t.setStartsAt(startsAt);
        t.setServerId(serverId);
        t.setSort(sort);
        return tournaments.save(t);
    }

    private void item(Long eventId, Instant start, String title, String where, String color, Long tournamentId) {
        schedule.save(new ScheduleItem(eventId, start, null, title, where, color, tournamentId));
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }
}
