package com.lanparty.dashboard.event;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.lanparty.dashboard.common.Json;

public final class EventDtos {

    private EventDtos() {
    }

    /** Section keys whose headings admins may override (with accent markup). */
    public static final Set<String> HEADING_KEYS = Set.of("servers", "schedule", "tournaments", "seating", "network");

    public record EventView(Long id, String slug, String title, String subtitle, String location, String timezone,
                            Instant startsAt, Instant endsAt, boolean active, String welcomeTitle, String welcomeText,
                            String logoUrl, int kioskIntervalSec, String kioskViews, String loginHeadline,
                            Map<String, String> headings, NetworkDto network, SeatRulesDto seatRules) {

        public static EventView of(Event e) {
            String logo = e.getLogoContentType() == null ? null
                    : "/api/public/events/" + e.getId() + "/logo?v=" + Integer.toHexString(java.util.Arrays.hashCode(e.getLogo()));
            return new EventView(e.getId(), e.getSlug(), e.getTitle(), e.getSubtitle(), e.getLocation(), e.getTimezone(),
                    e.getStartsAt(), e.getEndsAt(), e.isActive(), e.getWelcomeTitle(), e.getWelcomeText(), logo,
                    e.getKioskIntervalSec(), e.getKioskViews(), e.getLoginHeadline(), headings(e.getHeadings()),
                    NetworkDto.of(e.getNetwork()), SeatRulesDto.of(e.getSeatRules()));
        }

        private static Map<String, String> headings(String json) {
            Map<String, String> out = new TreeMap<>();
            Json.parse(json).properties().forEach(en -> {
                if (HEADING_KEYS.contains(en.getKey()) && en.getValue().isString() && !en.getValue().asString().isBlank()) {
                    out.put(en.getKey(), en.getValue().asString());
                }
            });
            return out;
        }
    }

    public record NetworkDto(
            @Size(max = 32) String wifiSsid,
            @Size(max = 63) String wifiPassword,
            @NotNull WifiSecurity wifiSecurity,
            boolean wifiHidden,
            @Size(max = 60) String lanIpMode,
            @Size(max = 60) String lanSubnet,
            @Size(max = 60) String lanGateway,
            @Size(max = 120) String tsAddress,
            @Min(1) @Max(65535) Integer tsPort,
            @Size(max = 60) String tsPassword) {

        static NetworkDto of(NetworkInfo n) {
            return new NetworkDto(n.getWifiSsid(), n.getWifiPassword(), n.getWifiSecurity(), n.isWifiHidden(), n.getLanIpMode(),
                    n.getLanSubnet(), n.getLanGateway(), n.getTsAddress(), n.getTsPort(), n.getTsPassword());
        }
    }

    public record SeatRulesDto(boolean selectionOpen, boolean changeAllowed, boolean approvalRequired,
                               @Size(max = 500) String info) {

        static SeatRulesDto of(SeatRules r) {
            return new SeatRulesDto(r.isSeatSelectionOpen(), r.isSeatChangeAllowed(), r.isSeatApprovalRequired(), r.getSeatInfo());
        }
    }

    public record EventRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 200) String subtitle,
            @Size(max = 200) String location,
            @Size(max = 60) String timezone,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @Size(max = 300) String welcomeTitle,
            @Size(max = 4000) String welcomeText,
            @Min(5) @Max(600) int kioskIntervalSec,
            @Size(max = 200) String kioskViews,
            @Size(max = 300) String loginHeadline,
            Map<String, @Size(max = 120) String> headings) {
    }

    /** Creates a new event; {@code cloneFromId} copies setup (infos, servers, seats, tournaments, integrations, schedule). */
    public record CreateEventRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9-]+", message = "nur a-z, 0-9 und -") String slug,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            Long cloneFromId) {
    }
}
