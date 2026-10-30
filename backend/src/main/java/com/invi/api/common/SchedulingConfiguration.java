package com.invi.api.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** App-wide scheduling switch and the clock every scheduled job and lifecycle rule reads. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration {
    @Bean
    public Clock clock() { return Clock.systemUTC(); }
}
