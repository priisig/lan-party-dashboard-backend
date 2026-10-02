package com.lanparty.dashboard.seating;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.lanparty.dashboard.event.BeamerSide;

public final class SeatingDtos {

    private SeatingDtos() {
    }

    /** @param pending true when a reservation for this seat waits for approval */
    public record SeatView(String label, int number, SeatStatus status, String gamertag, boolean pending, String note) {
    }

    public record RowView(Long id, String label, List<SeatView> seats) {
    }

    public record SeatMapView(BeamerSide beamerSide, String labelStart, String labelEnd, List<RowView> rows,
                              int taken, int free, int blocked, int total) {
    }

    public record ReservationRequest(@NotBlank @Size(max = 60) String gamertag, @Size(max = 300) String companions) {
    }

    public record PendingRequest(Long id, String seat, String gamertag, String companions, Instant createdAt) {
    }

    public record AssignRequest(@Size(max = 60) String gamertag, @Size(max = 300) String note) {
    }

    public record RowLayout(Long id,
                            @NotBlank @Size(max = 10) @Pattern(regexp = "[A-Za-z0-9]+", message = "nur Buchstaben/Ziffern") String label,
                            @Min(1) @Max(100) int seatCount) {
    }

    public record LayoutRequest(@NotNull BeamerSide beamerSide, @Size(max = 60) String labelStart,
                                @Size(max = 60) String labelEnd, @NotNull @Size(min = 1, max = 26) List<@Valid RowLayout> rows) {
    }
}
