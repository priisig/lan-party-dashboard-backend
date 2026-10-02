package com.lanparty.dashboard.auth;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lanparty.dashboard.user.AppUser;
import com.lanparty.dashboard.user.UserDtos.LoginRequest;
import com.lanparty.dashboard.user.UserDtos.Me;
import com.lanparty.dashboard.user.UserDtos.RegisterRequest;
import com.lanparty.dashboard.user.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService users;
    private final LoginAttemptService attempts;
    private final SecurityContextRepository contextRepository;

    public AuthController(UserService users, LoginAttemptService attempts, SecurityContextRepository contextRepository) {
        this.users = users;
        this.attempts = attempts;
        this.contextRepository = contextRepository;
    }

    @PostMapping("/register")
    public Me register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request, HttpServletResponse response) {
        AppUser user = users.register(body);
        signIn(user, request, response);
        return Me.of(user);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        String client = request.getRemoteAddr();
        var lock = attempts.lockRemaining(client);
        if (!lock.isZero()) {
            long minutes = Math.max(1, (lock.toSeconds() + 59) / 60);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Zu viele Fehlversuche. Bitte in " + minutes + " Min. erneut versuchen."));
        }
        var user = users.authenticate(body.login(), body.password()).orElse(null);
        if (user == null) {
            attempts.failed(client);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login oder Passwort falsch."));
        }
        attempts.succeeded(client);
        signIn(user, request, response);
        return ResponseEntity.ok(Me.of(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Nicht eingeloggt."));
        }
        return users.findActive(principal.id())
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(Me.of(u)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Nicht eingeloggt.")));
    }

    /** Touching the token makes Spring set the XSRF-TOKEN cookie, so the SPA can call this once on start. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    private void signIn(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        // New session id on login (session fixation protection).
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication(UserPrincipal.of(user)));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    static Authentication authentication(UserPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
    }
}
