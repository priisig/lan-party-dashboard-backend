package com.lanparty.dashboard.user;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.common.BadRequestException;
import com.lanparty.dashboard.common.NotFoundException;
import com.lanparty.dashboard.event.Event;
import com.lanparty.dashboard.user.UserDtos.Profile;
import com.lanparty.dashboard.user.UserDtos.RegisterRequest;

@Service
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AppUserRepository users;
    private final ParticipantRepository participants;
    private final PasswordEncoder encoder;
    /** Compared against when the login name is unknown, so both cases take the same time. */
    private final String dummyHash;

    public UserService(AppUserRepository users, ParticipantRepository participants, PasswordEncoder encoder) {
        this.users = users;
        this.participants = participants;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("timing-dummy-password");
    }

    @Transactional
    public AppUser register(RegisterRequest r) {
        return create(r.nickname(), r.email(), r.password(), UserRole.USER);
    }

    @Transactional
    public AppUser create(String nickname, String email, String password, UserRole role) {
        String nick = nickname.trim();
        String mail = email.trim().toLowerCase();
        if (users.existsByNicknameIgnoreCase(nick)) {
            throw new BadRequestException("Der Nickname «" + nick + "» ist schon vergeben.");
        }
        if (users.existsByEmailIgnoreCase(mail)) {
            throw new BadRequestException("Mit dieser E-Mail gibt es schon einen Account.");
        }
        try {
            return users.saveAndFlush(new AppUser(nick, mail, encoder.encode(password), role));
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("Nickname oder E-Mail ist schon vergeben.");
        }
    }

    /** @param login e-mail address or nickname */
    @Transactional(readOnly = true)
    public Optional<AppUser> authenticate(String login, String password) {
        String l = login.trim();
        Optional<AppUser> user = l.contains("@") ? users.findByEmailIgnoreCase(l) : users.findByNicknameIgnoreCase(l);
        if (user.isEmpty()) {
            encoder.matches(password, dummyHash);
            return Optional.empty();
        }
        AppUser u = user.get();
        return u.isEnabled() && encoder.matches(password, u.getPasswordHash()) ? user : Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findActive(Long id) {
        return users.findById(id).filter(AppUser::isEnabled);
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Account nicht gefunden."));
    }

    @Transactional
    public AppUser updateProfile(Long id, Profile p) {
        AppUser u = get(id);
        String nick = p.nickname().trim();
        String mail = p.email().trim().toLowerCase();
        users.findByNicknameIgnoreCase(nick).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BadRequestException("Der Nickname «" + nick + "» ist schon vergeben.");
        });
        users.findByEmailIgnoreCase(mail).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BadRequestException("Mit dieser E-Mail gibt es schon einen Account.");
        });
        u.setNickname(nick);
        u.setEmail(mail);
        u.setFirstName(blankToNull(p.firstName()));
        u.setLastName(blankToNull(p.lastName()));
        u.setSteam(blankToNull(p.steam()));
        u.setDiscord(blankToNull(p.discord()));
        u.setTeam(blankToNull(p.team()));
        u.setFavouriteGame(blankToNull(p.favouriteGame()));
        u.setShowOnSeatmap(p.showOnSeatmap());
        return u;
    }

    @Transactional
    public void changePassword(Long id, String current, String next) {
        AppUser u = get(id);
        if (!encoder.matches(current, u.getPasswordHash())) {
            throw new BadRequestException("Das aktuelle Passwort stimmt nicht.");
        }
        u.setPasswordHash(encoder.encode(next));
    }

    /** Creates the participant row for this event if it doesn't exist yet. */
    @Transactional
    public Participant participant(Event event, Long userId) {
        return participants.findByEventIdAndUserId(event.getId(), userId)
                .orElseGet(() -> participants.save(new Participant(event.getId(), userId)));
    }

    @Transactional(readOnly = true)
    public int lanCount(Long userId) {
        return (int) participants.countByUserId(userId);
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        return users.findAllByOrderByNicknameAsc();
    }

    @Transactional
    public void setRole(Long id, UserRole role, Long currentUserId) {
        AppUser u = get(id);
        if (u.getRole() == role) {
            return;
        }
        if (role != UserRole.ORGA) {
            if (id.equals(currentUserId)) {
                throw new BadRequestException("Du kannst dir die Orga-Rolle nicht selbst entziehen.");
            }
            ensureAnotherOrga(u);
        }
        u.setRole(role);
    }

    @Transactional
    public void setEnabled(Long id, boolean enabled, Long currentUserId) {
        AppUser u = get(id);
        if (!enabled) {
            if (id.equals(currentUserId)) {
                throw new BadRequestException("Du kannst dich nicht selbst sperren.");
            }
            if (u.isOrga()) {
                ensureAnotherOrga(u);
            }
        }
        u.setEnabled(enabled);
    }

    /** Sets a random password and returns it once, so an orga can hand it over (there is no e-mail). */
    @Transactional
    public String resetPassword(Long id) {
        AppUser u = get(id);
        StringBuilder pw = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            pw.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        u.setPasswordHash(encoder.encode(pw.toString()));
        return pw.toString();
    }

    @Transactional(readOnly = true)
    public boolean noOrga() {
        return users.countByRoleAndEnabledTrue(UserRole.ORGA) == 0;
    }

    private void ensureAnotherOrga(AppUser u) {
        if (u.isOrga() && u.isEnabled() && users.countByRoleAndEnabledTrue(UserRole.ORGA) <= 1) {
            throw new BadRequestException("Es braucht mindestens einen aktiven Orga-Account.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
