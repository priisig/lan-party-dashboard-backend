package com.lanparty.dashboard.admin;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Global key/value settings that are not tied to a single event (API keys, tokens). */
@Service
public class Settings {

    public static final String CHALLONGE_API_KEY = "challonge.apiKey";
    public static final String PUSH_TOKEN = "stats.pushToken";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppSettingRepository repository;

    public Settings(AppSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        return repository.findById(key).map(AppSetting::getValue).filter(v -> !v.isBlank());
    }

    @Transactional
    public void set(String key, String value) {
        repository.findById(key).ifPresentOrElse(
                s -> s.setValue(value),
                () -> repository.save(new AppSetting(key, value)));
    }

    /** Returns the push token, creating a random one on first use. */
    @Transactional
    public String pushToken() {
        return get(PUSH_TOKEN).orElseGet(this::regeneratePushToken);
    }

    @Transactional
    public String regeneratePushToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        set(PUSH_TOKEN, token);
        return token;
    }
}
