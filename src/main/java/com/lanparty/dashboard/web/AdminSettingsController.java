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

import com.lanparty.dashboard.admin.Admin;
import com.lanparty.dashboard.admin.AdminService;
import com.lanparty.dashboard.admin.Settings;
import com.lanparty.dashboard.auth.AdminPrincipal;

/** Global settings (Challonge key, push token) and admin accounts. */
@RestController
@RequestMapping("/api/admin")
public class AdminSettingsController {

    private final Settings settings;
    private final AdminService admins;

    public AdminSettingsController(Settings settings, AdminService admins) {
        this.settings = settings;
        this.admins = admins;
    }

    public record SettingsView(boolean challongeConfigured, String challongeKeyHint, String pushToken) {
    }

    public record ChallongeKeyRequest(@Size(max = 200) String apiKey) {
    }

    public record AdminView(Long id, String name, String codeHint) {
        static AdminView of(Admin a) {
            return new AdminView(a.getId(), a.getName(), a.getCodeHint());
        }
    }

    /** {@code code} is only returned once, right after creating or regenerating. */
    public record AdminWithCode(AdminView admin, String code) {
    }

    public record CreateAdminRequest(@NotBlank @Size(max = 60) String name) {
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

    @GetMapping("/admins")
    public List<AdminView> admins() {
        return admins.list().stream().map(AdminView::of).toList();
    }

    @PostMapping("/admins")
    public AdminWithCode createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        var created = admins.create(request.name());
        return new AdminWithCode(AdminView.of(created.admin()), created.code());
    }

    @PostMapping("/admins/{id}/code")
    public AdminWithCode regenerate(@PathVariable Long id) {
        var created = admins.regenerateCode(id);
        return new AdminWithCode(AdminView.of(created.admin()), created.code());
    }

    @DeleteMapping("/admins/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal me) {
        admins.delete(id, me.id());
        return ResponseEntity.noContent().build();
    }
}
