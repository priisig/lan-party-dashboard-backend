package com.lanparty.dashboard.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lanparty.dashboard.admin.Settings;

/** Global settings (Challonge key, push token). Accounts are managed in AdminUserController. */
@RestController
@RequestMapping("/api/admin")
public class AdminSettingsController {

    private final Settings settings;
    public AdminSettingsController(Settings settings) {
        this.settings = settings;
    }

    public record SettingsView(boolean challongeConfigured, String challongeKeyHint, String pushToken) {
    }

    public record ChallongeKeyRequest(@Size(max = 200) String apiKey) {
    }

    @GetMapping("/settings")
    public SettingsView settings() {
        var key = settings.get(Settings.CHALLONGE_API_KEY);
        return new SettingsView(key.isPresent(), key.map(k -> "…" + k.substring(Math.max(0, k.length() - 4))).orElse(null),
                settings.pushToken());
    }

    @PutMapping("/settings/challonge")
    public SettingsView challongeKey(@Valid @RequestBody ChallongeKeyRequest request) {
        settings.set(Settings.CHALLONGE_API_KEY, request.apiKey() == null ? "" : request.apiKey().trim());
        return settings();
    }

    @PostMapping("/settings/push-token")
    public SettingsView regeneratePushToken() {
        settings.regeneratePushToken();
        return settings();
    }
}
