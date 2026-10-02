package com.lanparty.dashboard.challonge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.lanparty.dashboard.admin.Settings;
import com.lanparty.dashboard.common.ExternalServiceException;

class ChallongeClientTest {

    private MockRestServiceServer server;
    private ChallongeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Settings settings = new Settings(null) {
            @Override
            public Optional<String> get(String key) {
                return Optional.of("KEY");
            }
        };
        client = new ChallongeClient(builder.baseUrl("https://api.test/v1").build(), settings);
    }

    @Test
    void fetchesTournamentWithParticipantsAndMatches() {
        server.expect(requestTo("https://api.test/v1/tournaments/vivo_cs2.json?api_key=KEY&include_participants=1&include_matches=1"))
                .andRespond(withSuccess("{\"tournament\":{}}", MediaType.APPLICATION_JSON));
        assertThat(client.fetchTournament("vivo_cs2")).isEqualTo("{\"tournament\":{}}");
        server.verify();
    }

    @Test
    void addsParticipantAsForm() {
        server.expect(requestTo("https://api.test/v1/tournaments/vivo_cs2/participants.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("api_key", "KEY", "participant[name]", "Rush B")))
                .andRespond(withSuccess("{\"participant\":{\"id\":4711}}", MediaType.APPLICATION_JSON));
        assertThat(client.addParticipant("vivo_cs2", "Rush B", null)).isEqualTo(4711L);
        server.verify();
    }

    @Test
    void surfacesChallongeErrorMessages() {
        server.expect(requestTo("https://api.test/v1/tournaments/x/participants.json"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[\"Name has already been taken\"]}"));
        assertThatThrownBy(() -> client.addParticipant("x", "dup", null))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("Challonge: 422 Name has already been taken");
    }

    @Test
    void nonJsonErrorBodiesStillGiveACleanError() {
        server.expect(requestTo("https://api.test/v1/tournaments/x/start.json"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>Bad gateway</html>"));
        assertThatThrownBy(() -> client.start("x"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageStartingWith("Challonge: 502");
    }
}
