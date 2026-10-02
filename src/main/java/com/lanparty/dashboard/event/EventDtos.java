package com.lanparty.dashboard.event;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class EventDtos {

    private EventDtos() {
    }

    public record EventView(Long id, String slug, String title, String subtitle, String location, String timezone,
                            Instant startsAt, Instant endsAt, boolean active, String welcomeTitle, String welcomeText,
                            String logoUrl, int kioskIntervalSec, String kioskViews) {

        public static EventView of(Event e) {
            String logo = e.getLogoContentType() == null ? null
                    : "/api/public/events/" + e.getId() + "/logo?v=" + Integer.toHexString(java.util.Arrays.hashCode(e.getLogo()));
            return new EventView(e.getId(), e.getSlug(), e.getTitle(), e.getSubtitle(), e.getLocation(), e.getTimezone(),
                    e.getStartsAt(), e.getEndsAt(), e.isActive(), e.getWelcomeTitle(), e.getWelcomeText(), logo,
                    e.getKioskIntervalSec(), e.getKioskViews());
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
            @Size(max = 200) String kioskViews) {
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
