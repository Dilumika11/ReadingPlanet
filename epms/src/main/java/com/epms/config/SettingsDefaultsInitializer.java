package com.epms.config;

import com.epms.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Creates the well-known system settings (currency, tax rate, ...) with defaults if they are missing. */
@Component
@Order(1)
@RequiredArgsConstructor
public class SettingsDefaultsInitializer implements CommandLineRunner {

    private final SettingsService settingsService;

    @Override
    public void run(String... args) {
        settingsService.ensureDefaults();
    }
}
