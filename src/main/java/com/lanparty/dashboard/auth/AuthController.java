package com.lanparty.dashboard.auth;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;

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

import com.lanparty.dashboard.admin.AdminService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminService admins;
    private final LoginAttemptService attempts;
    private final SecurityContextRepository contextRepository;

    public AuthController(AdminService admins, LoginAttemptService attempts, SecurityContextRepository contextRepository) {
        this.admins = admins;
        this.attempts = attempts;
        this.contextRepository = contextRepository;
    }

    public record LoginRequest(@NotBlank String code) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        String client = request.getRemoteAddr();
        var lock = attempts.lockRemaining(client);
        if (!lock.isZero()) {
            long minutes = Math.max(1, (lock.toSeconds() + 59) / 60);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Zu viele Fehlversuche. Bitte in " + minutes + " Min. erneut versuchen."));
        }
        var admin = body.code() == null ? null : admins.findByCode(body.code().trim()).orElse(null);
        if (admin == null) {
            attempts.failed(client);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Code ungültig."));
        }
        attempts.succeeded(client);

        // New session id on login (session fixation protection).
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        request.getSession(true);
        var principal = new AdminPrincipal(admin.getId(), admin.getName());
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return ResponseEntity.ok(principal);
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
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Nicht eingeloggt."));
        }
        return ResponseEntity.ok(principal);
    }

    /** Touching the token makes Spring set the XSRF-TOKEN cookie, so the SPA can call this once on start. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
}
