package com.lanparty.dashboard.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.lanparty.dashboard.config.LanProperties;

/** Creates the first organiser account from LAN_BOOTSTRAP_ADMIN_* so a fresh installation can be configured. */
@Component
@Order(1)
public class OrgaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrgaBootstrap.class);

    private final UserService users;
    private final AppUserRepository repository;
    private final LanProperties properties;

    public OrgaBootstrap(UserService users, AppUserRepository repository, LanProperties properties) {
        this.users = users;
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.noOrga()) {
            return;
        }
        String email = properties.bootstrapAdminEmail();
        String password = properties.bootstrapAdminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No organiser account exists – set LAN_BOOTSTRAP_ADMIN_EMAIL and LAN_BOOTSTRAP_ADMIN_PASSWORD to create one.");
            return;
        }
        if (password.length() < 8) {
            log.error("LAN_BOOTSTRAP_ADMIN_PASSWORD must have at least 8 characters – no organiser created.");
            return;
        }
        // An existing account with that e-mail (e.g. registered before) is promoted instead.
        var existing = repository.findByEmailIgnoreCase(email.trim());
        if (existing.isPresent()) {
            existing.get().setRole(UserRole.ORGA);
            existing.get().setEnabled(true);
            repository.save(existing.get());
            log.info("Promoted '{}' to organiser.", existing.get().getNickname());
            return;
        }
        users.create(properties.bootstrapAdminNickname(), email, password, UserRole.ORGA);
        log.info("Created organiser account '{}'.", properties.bootstrapAdminNickname());
    }
}
