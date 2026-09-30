package com.fitconnect.bookingservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active les taches planifiees ; desactivable (tests) via booking.scheduler.enabled=false.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "booking.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
