package com.lanparty.dashboard.user;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    static final String NICK_PATTERN = "[\\p{L}\\p{N}_.\\- ]+";

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 40) @Pattern(regexp = NICK_PATTERN, message = "nur Buchstaben, Ziffern, Leerzeichen und _ . -") String nickname,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @AssertTrue(message = "Bitte Hausordnung und Datenschutz akzeptieren.") boolean rulesAccepted) {
    }

    /** {@code login} is the e-mail address or the nickname. */
    public record LoginRequest(@NotBlank String login, @NotBlank String password) {
    }

    /** Session info for the frontend. */
    public record Me(Long id, String nickname, String email, UserRole role) {

        public static Me of(AppUser u) {
            return new Me(u.getId(), u.getNickname(), u.getEmail(), u.getRole());
        }
    }

    public record Profile(
            @NotBlank @Size(min = 2, max = 40) @Pattern(regexp = NICK_PATTERN, message = "nur Buchstaben, Ziffern, Leerzeichen und _ . -") String nickname,
            @NotBlank @Email @Size(max = 200) String email,
            @Size(max = 60) String firstName,
            @Size(max = 60) String lastName,
            @Size(max = 60) String steam,
            @Size(max = 60) String discord,
            @Size(max = 80) String team,
            @Size(max = 80) String favouriteGame,
            boolean showOnSeatmap) {

        public static Profile of(AppUser u) {
            return new Profile(u.getNickname(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getSteam(), u.getDiscord(),
                    u.getTeam(), u.getFavouriteGame(), u.isShowOnSeatmap());
        }
    }

    public record PasswordChange(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    /** One of my tournament sign-ups in the active event. */
    public record MyTournament(Long tournamentId, String name, String color, String teamName, String statusText) {
    }

    /** @param seatPending seat label of a reservation waiting for approval */
    public record MyEvent(String seat, String seatPending, boolean paid, boolean checkedIn, List<MyTournament> tournaments,
                          int lanCount) {
    }

    // ---------------------------------------------------------------- admin

    public record AdminUserView(Long id, String nickname, String email, UserRole role, boolean enabled, String firstName,
                                String lastName, Instant createdAt, boolean participant, String seat, boolean paid,
                                boolean checkedIn) {
    }

    public record ParticipantUpdate(boolean paid, boolean checkedIn) {
    }

    public record RoleUpdate(@NotNull UserRole role) {
    }

    public record EnabledUpdate(boolean enabled) {
    }

    /** The generated password is only returned once. */
    public record PasswordReset(String password) {
    }
}
