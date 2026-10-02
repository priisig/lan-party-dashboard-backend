package com.lanparty.dashboard.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Injected everywhere "now" matters so tests can pin the time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
