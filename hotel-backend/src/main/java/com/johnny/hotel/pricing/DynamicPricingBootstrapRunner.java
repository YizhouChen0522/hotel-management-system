package com.johnny.hotel.pricing;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Recovers a missed first-administrator bootstrap after Flyway and application startup. */
@Component @RequiredArgsConstructor
public class DynamicPricingBootstrapRunner implements ApplicationRunner {
    private final DynamicPricingBootstrapService bootstrap;

    @Override public void run(ApplicationArguments args) {
        Long creator = bootstrap.firstEligibleCreator();
        if (creator != null) bootstrap.ensureDefaultTemplate(creator);
    }
}
