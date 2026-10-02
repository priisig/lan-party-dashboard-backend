package com.lanparty.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
        "lan.bootstrap-admin-code=87654321",
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
        MockHttpSession session = new MockHttpSession();
        var result = mvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"87654321\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
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
    void adminApiNeedsLoginAndCsrf() throws Exception {
        mvc.perform(get("/api/admin/events")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"87654321\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
        MockHttpSession session = login();
        mvc.perform(get("/api/admin/events").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.name").value("Admin"));
    }

    @Test
    void registrationEnforcesTeamNameAndCapacity() throws Exception {
        String base = "/api/public/tournaments/2/registrations"; // CoD4 2v2: 14/16
        mvc.perform(post(base).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gamertag\":\"x\",\"rulesAccepted\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Für dieses Teamturnier braucht es einen Teamnamen."));
        for (String team : new String[] {"Cap A", "Cap B"}) {
            mvc.perform(post(base).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"gamertag\":\"p\",\"teamName\":\"" + team + "\",\"rulesAccepted\":true}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(base).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gamertag\":\"p\",\"teamName\":\"Cap C\",\"rulesAccepted\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CoD4 2v2 ist bereits voll (16)."));
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
