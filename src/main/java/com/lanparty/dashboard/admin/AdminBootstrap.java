package com.lanparty.dashboard.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.lanparty.dashboard.config.LanProperties;

/** Creates the first admin from LAN_BOOTSTRAP_ADMIN_CODE so a fresh installation can be configured. */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminService admins;
    private final LanProperties properties;

    public AdminBootstrap(AdminService admins, LanProperties properties) {
        this.admins = admins;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!admins.none()) {
            return;
        }
        String code = properties.bootstrapAdminCode();
        if (code == null || code.isBlank()) {
            log.warn("No admin exists and lan.bootstrap-admin-code is empty – set LAN_BOOTSTRAP_ADMIN_CODE to create the first admin.");
            return;
        }
        if (code.trim().length() < 6) {
            log.error("LAN_BOOTSTRAP_ADMIN_CODE must have at least 6 characters – no admin created.");
            return;
        }
        admins.create(properties.bootstrapAdminName(), code.trim());
        log.info("Created bootstrap admin '{}'.", properties.bootstrapAdminName());
    }
}
