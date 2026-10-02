package com.lanparty.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.lanparty.dashboard.common.Json;
import com.lanparty.dashboard.event.EventRepository;
import com.lanparty.dashboard.schedule.ScheduleItemRepository;
import com.lanparty.dashboard.seating.SeatRepository;
import com.lanparty.dashboard.seating.SeatStatus;

import tools.jackson.databind.JsonNode;

@SpringBootTest(properties = {
        "lan.seed-demo=true",
        "lan.bootstrap-admin-email=orga@lan.test",
        "lan.bootstrap-admin-password=orga-pass-123",
        "lan.integration-poll-ms=3600000",
        "lan.server-query-ms=3600000",
        "lan.challonge-poll-ms=3600000"})
@AutoConfigureMockMvc
@Testcontainers
class DashboardIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;
    @Autowired
    EventRepository events;
    @Autowired
    SeatRepository seats;
    @Autowired
    ScheduleItemRepository schedule;

    private MockHttpSession login() throws Exception {
        return login("orga@lan.test", "orga-pass-123");
    }

    private MockHttpSession login(String login, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        var result = mvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    private static int users = 0;

    /** Registers a fresh participant account and returns its session. */
    private MockHttpSession register() throws Exception {
        String nick = "player" + (++users) + "_" + System.nanoTime() % 100000;
        var result = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nick + "\",\"email\":\"" + nick + "@lan.test\",\"password\":\"secret-123\",\"rulesAccepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    private void seatRules(boolean open, boolean change, boolean approval) throws Exception {
        Long eventId = events.findFirstByActiveTrue().orElseThrow().getId();
        mvc.perform(put("/api/admin/events/" + eventId + "/seat-rules").session(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectionOpen\":" + open + ",\"changeAllowed\":" + change + ",\"approvalRequired\":" + approval + "}"))
                .andExpect(status().isOk());
    }

    private java.util.List<String> freeSeats() {
        Long eventId = events.findFirstByActiveTrue().orElseThrow().getId();
        return seats.findByEventId(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.FREE && !s.getLabel().equals("B4"))
                .map(s -> s.getLabel()).sorted().toList();
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return Json.parse(actions.andReturn().getResponse().getContentAsString());
    }

    @Test
    void publicEndpointsServeTheSeededEvent() throws Exception {
        mvc.perform(get("/api/public/event")).andExpect(status().isOk())
                .andExpect(jsonPath("$.event.title").value("VIVO LAN 2026"))
                .andExpect(jsonPath("$.infos.length()").value(5));
        mvc.perform(get("/api/public/live")).andExpect(jsonPath("$.text").value("CS2 5v5 – Viertelfinal"));
        mvc.perform(get("/api/public/banners")).andExpect(jsonPath("$[0].kind").value("REGISTRATION_CLOSING"));
        mvc.perform(get("/api/public/seats")).andExpect(jsonPath("$.total").value(20)).andExpect(jsonPath("$.orientation").value("COLUMNS")).andExpect(jsonPath("$.markers[0].kind").value("BEAMER"));
        mvc.perform(get("/api/public/tournaments/1")).andExpect(jsonPath("$.bracket.rounds.length()").value(3));
        mvc.perform(get("/api/public/stats")).andExpect(jsonPath("$.widgets.length()").value(2));
    }

    @Test
    void adminApiNeedsAnOrgaAccountAndCsrf() throws Exception {
        mvc.perform(get("/api/admin/events")).andExpect(status().isUnauthorized());
        String body = "{\"login\":\"orga@lan.test\",\"password\":\"orga-pass-123\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"orga@lan.test\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        MockHttpSession session = login();
        mvc.perform(get("/api/admin/events").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.nickname").value("Orga"))
                .andExpect(jsonPath("$.role").value("ORGA"));
        // Nickname works as login, too.
        login("orga", "orga-pass-123");

        MockHttpSession user = register();
        mvc.perform(get("/api/admin/events").session(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/profile").session(user)).andExpect(status().isOk());
        mvc.perform(get("/api/me/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void promotingAUserTakesEffectWithoutNewLogin() throws Exception {
        MockHttpSession user = register();
        long id = json(mvc.perform(get("/api/auth/me").session(user))).path("id").asLong();
        mvc.perform(get("/api/admin/events").session(user)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/" + id + "/role").session(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ORGA\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/events").session(user)).andExpect(status().isOk());

        String pw = json(mvc.perform(post("/api/admin/users/" + id + "/password-reset").session(login()).with(csrf()))
                .andExpect(status().isOk())).path("password").asString();
        assertThat(pw).hasSize(10);
        mvc.perform(put("/api/admin/users/" + id + "/enabled").session(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/events").session(user)).andExpect(status().isUnauthorized());
    }

    @Test
    void registeringTwiceWithTheSameNicknameFails() throws Exception {
        String body = "{\"nickname\":\"Orga\",\"email\":\"other@lan.test\",\"password\":\"secret-123\",\"rulesAccepted\":true}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Der Nickname «Orga» ist schon vergeben."));
    }

    @Test
    void seatSelfServiceFollowsTheEventRules() throws Exception {
        MockHttpSession a = register();
        MockHttpSession b = register();
        Long eventId = events.findFirstByActiveTrue().orElseThrow().getId();

        // Approval required (seeded): a request, not a booking.
        seatRules(true, true, true);
        String first = freeSeats().getFirst();
        mvc.perform(post("/api/me/seat/" + first).session(a).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatPending").value(first))
                .andExpect(jsonPath("$.seat").isEmpty());
        mvc.perform(post("/api/me/seat/" + first).session(b).with(csrf()))
                .andExpect(status().isBadRequest());
        JsonNode pending = json(mvc.perform(get("/api/admin/events/" + eventId + "/seats/requests").session(login())));
        long requestId = 0;
        for (JsonNode p : pending) {
            if (p.path("seat").asString().equals(first)) {
                requestId = p.path("id").asLong();
            }
        }
        mvc.perform(post("/api/admin/events/" + eventId + "/seats/requests/" + requestId + "/approve").session(login()).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/event").session(a)).andExpect(jsonPath("$.seat").value(first));

        // Direct booking and moving.
        seatRules(true, true, false);
        String second = freeSeats().getFirst();
        mvc.perform(post("/api/me/seat/" + second).session(a).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seat").value(second));
        assertThat(seats.findByEventIdAndLabelIgnoreCase(eventId, first).orElseThrow().getStatus()).isEqualTo(SeatStatus.FREE);

        // Changes locked, selection closed.
        seatRules(true, false, false);
        mvc.perform(post("/api/me/seat/" + first).session(a).with(csrf()))
                .andExpect(status().isBadRequest());
        seatRules(false, true, false);
        mvc.perform(post("/api/me/seat/" + first).session(b).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Die Platzwahl ist geschlossen – bitte wende dich an die Orga."));

        mvc.perform(delete("/api/me/seat").session(a).with(csrf())).andExpect(jsonPath("$.seat").isEmpty());
        seatRules(true, true, true);
    }

    @Test
    void registrationNeedsAnAccountAndEnforcesTeamNameAndCapacity() throws Exception {
        String base = "/api/me/tournaments/2"; // CoD4 2v2: 14/16
        mvc.perform(post(base).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"rulesAccepted\":true}"))
                .andExpect(status().isUnauthorized());
        MockHttpSession first = register();
        mvc.perform(post(base).session(first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rulesAccepted\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Für dieses Teamturnier braucht es einen Teamnamen."));
        mvc.perform(post(base).session(first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"Cap A\",\"rulesAccepted\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post(base).session(first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"Cap X\",\"rulesAccepted\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Du bist für CoD4 2v2 bereits angemeldet."));
        mvc.perform(get("/api/me/event").session(first)).andExpect(jsonPath("$.tournaments[0].teamName").value("Cap A"));
        mvc.perform(post(base).session(register()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"Cap B\",\"rulesAccepted\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post(base).session(register()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"Cap C\",\"rulesAccepted\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CoD4 2v2 ist bereits voll (16)."));
        // Withdrawing frees the slot again.
        mvc.perform(delete(base).session(first).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/me/event").session(first)).andExpect(jsonPath("$.tournaments.length()").value(0));
    }

    @Test
    void layoutChangeKeepsAssignmentsOfRemainingSeats() throws Exception {
        MockHttpSession session = login();
        Long eventId = events.findFirstByActiveTrue().orElseThrow().getId();
        JsonNode map = json(mvc.perform(put("/api/admin/events/" + eventId + "/seats/layout").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orientation\":\"COLUMNS\",\"rowsReversed\":false,\"numbersReversed\":false,"
                        + "\"markers\":[{\"kind\":\"BEAMER\",\"label\":\"Beamer\",\"side\":\"BOTTOM\",\"align\":\"CENTER\"},"
                        + "{\"kind\":\"ENTRANCE\",\"label\":\"Eingang\",\"side\":\"LEFT\",\"align\":\"START\"}],"
                        + "\"rows\":[{\"id\":1,\"label\":\"A\",\"seatCount\":5},{\"id\":2,\"label\":\"B\",\"seatCount\":10},{\"label\":\"C\",\"seatCount\":3}]}"))
                .andExpect(status().isOk()));
        // Moving the beamer to the bottom must not rotate the seats.
        assertThat(map.path("orientation").asString()).isEqualTo("COLUMNS");
        assertThat(map.path("markers").get(0).path("side").asString()).isEqualTo("BOTTOM");
        assertThat(map.path("markers").get(1).path("kind").asString()).isEqualTo("ENTRANCE");
        assertThat(map.path("total").asInt()).isEqualTo(18);
        assertThat(seats.findByEventIdAndLabelIgnoreCase(eventId, "A3").orElseThrow().getGamertag()).isEqualTo("Lag_L");
        assertThat(seats.findByEventIdAndLabelIgnoreCase(eventId, "A10")).isEmpty();
        assertThat(seats.findByEventIdAndLabelIgnoreCase(eventId, "C2").orElseThrow().getStatus()).isEqualTo(SeatStatus.FREE);
    }

    @Test
    void cloningAnEventShiftsTheScheduleAndKeepsOrgaSeats() throws Exception {
        MockHttpSession session = login();
        var source = events.findFirstByActiveTrue().orElseThrow();
        Instant start = source.getStartsAt().plus(Duration.ofDays(364));
        Instant end = source.getEndsAt().plus(Duration.ofDays(364));
        JsonNode created = json(mvc.perform(post("/api/admin/events").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"VIVO LAN 2027\",\"slug\":\"vivo-lan-2027\",\"startsAt\":\"" + start + "\",\"endsAt\":\"" + end
                        + "\",\"cloneFromId\":" + source.getId() + "}"))
                .andExpect(status().isOk()));
        Long id = created.path("id").asLong();
        assertThat(created.path("active").asBoolean()).isFalse();
        assertThat(created.path("logoUrl").isNull()).isFalse();

        var oldItems = schedule.findByEventIdOrderByStartsAt(source.getId());
        var newItems = schedule.findByEventIdOrderByStartsAt(id);
        assertThat(newItems).hasSameSizeAs(oldItems);
        assertThat(newItems.getFirst().getStartsAt()).isEqualTo(oldItems.getFirst().getStartsAt().plus(Duration.ofDays(364)));

        var clonedSeats = seats.findByEventId(id);
        assertThat(clonedSeats).hasSize(seats.findByEventId(source.getId()).size());
        assertThat(clonedSeats).filteredOn(s -> s.getStatus() == SeatStatus.BLOCKED).hasSize(2);
        assertThat(clonedSeats).filteredOn(s -> s.getStatus() == SeatStatus.TAKEN).isEmpty();

        mvc.perform(get("/api/admin/events/" + id + "/tournaments").session(session))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[1].registrations.length()").value(0))
                .andExpect(jsonPath("$[0].challongeSlug").isEmpty());
    }
}
