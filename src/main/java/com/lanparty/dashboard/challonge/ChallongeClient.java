package com.lanparty.dashboard.challonge;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.lanparty.dashboard.admin.Settings;
import com.lanparty.dashboard.common.ExternalServiceException;
import com.lanparty.dashboard.common.Json;
import com.lanparty.dashboard.config.LanProperties;

import tools.jackson.databind.JsonNode;

/** Thin wrapper around the Challonge API v1 (https://api.challonge.com/v1), authenticated by API key. */
@Component
public class ChallongeClient {

    private final RestClient http;
    private final Settings settings;

    @org.springframework.beans.factory.annotation.Autowired
    public ChallongeClient(RestClient.Builder builder, LanProperties properties, Settings settings) {
        this(builder.baseUrl(properties.challongeBaseUrl()).requestFactory(timeouts()).build(), settings);
    }

    ChallongeClient(RestClient http, Settings settings) {
        this.http = http;
        this.settings = settings;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    public boolean configured() {
        return settings.get(Settings.CHALLONGE_API_KEY).isPresent();
    }

    /** Tournament incl. participants and matches, as raw JSON (the format we cache as snapshot). */
    public String fetchTournament(String slug) {
        return call(() -> http.get()
                .uri("/tournaments/{slug}.json?api_key={key}&include_participants=1&include_matches=1", slug, apiKey())
                .retrieve()
                .body(String.class));
    }

    /** Adds a participant and returns its Challonge id. */
    public long addParticipant(String slug, String name, String misc) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("api_key", apiKey());
        form.add("participant[name]", name);
        if (misc != null) {
            form.add("participant[misc]", misc);
        }
        String body = call(() -> http.post()
                .uri("/tournaments/{slug}/participants.json", slug)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class));
        JsonNode participant = Json.parse(body).path("participant");
        return participant.path("id").asLong();
    }

    public void start(String slug) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("api_key", apiKey());
        call(() -> http.post()
                .uri("/tournaments/{slug}/start.json", slug)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class));
    }

    private String apiKey() {
        return settings.get(Settings.CHALLONGE_API_KEY)
                .orElseThrow(() -> new ExternalServiceException("Kein Challonge API-Key hinterlegt (Admin → Integrationen)."));
    }

    private static String call(java.util.function.Supplier<String> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw new ExternalServiceException("Challonge: " + e.getStatusCode().value() + " " + errorDetail(e), e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Challonge nicht erreichbar: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (e instanceof ExternalServiceException) {
                throw e;
            }
            throw new ExternalServiceException("Challonge-Antwort ungültig: " + e.getMessage(), e);
        }
    }

    /** First entry of Challonge's {"errors": [...]}, or the HTTP status text for non-JSON bodies (e.g. proxy HTML pages). */
    private static String errorDetail(RestClientResponseException e) {
        try {
            JsonNode errors = Json.parse(e.getResponseBodyAsString()).path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                return errors.get(0).asString();
            }
        } catch (RuntimeException ignored) {
            // not JSON
        }
        return e.getStatusText();
    }

    /**
     * Accepts "vivolan_cs2", "https://challonge.com/vivolan_cs2" or "https://org.challonge.com/cup"
     * and returns the API identifier ("vivolan_cs2" / "org-cup").
     */
    public static String normalizeSlug(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String s = input.trim().replaceFirst("^https?://", "");
        int cut = s.indexOf('?') >= 0 ? s.indexOf('?') : s.indexOf('#');
        if (cut >= 0) {
            s = s.substring(0, cut);
        }
        List<String> parts = Arrays.stream(s.split("/")).filter(p -> !p.isBlank()).toList();
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        String host = parts.getFirst();
        // Skip a language prefix like challonge.com/de/xyz
        String path = parts.size() > 2 && parts.get(1).matches("[a-z]{2}") ? parts.get(2) : parts.get(1);
        if (host.endsWith(".challonge.com") && !host.startsWith("www.")) {
            return host.substring(0, host.indexOf('.')) + "-" + path;
        }
        return path;
    }
}
