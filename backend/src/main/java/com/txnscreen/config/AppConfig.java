package com.txnscreen.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application wide beans, and scheduling for the run store's expiry sweep.
 */
@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * The clock that run creation times and expiry are measured with.
     *
     * @return the system clock in UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
