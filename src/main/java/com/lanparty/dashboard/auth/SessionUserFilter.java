package com.lanparty.dashboard.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.lanparty.dashboard.user.UserService;

/**
 * Re-checks the account behind a session on every API call, so role changes and disabled accounts take effect
 * immediately instead of at the next login.
 */
public class SessionUserFilter extends OncePerRequestFilter {

    private final UserService users;
    private final SecurityContextRepository contextRepository;

    public SessionUserFilter(UserService users, SecurityContextRepository contextRepository) {
        this.users = users;
        this.contextRepository = contextRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/api/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            var current = users.findActive(principal.id()).map(UserPrincipal::of).orElse(null);
            if (current == null) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
            } else if (!current.equals(principal)) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(AuthController.authentication(current));
                SecurityContextHolder.setContext(context);
                contextRepository.saveContext(context, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
