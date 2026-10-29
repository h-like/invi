package com.invi.api.media;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MediaCleanupProperties.class)
public class MediaCleanupConfiguration {
    @Bean
    public Clock mediaCleanupClock() { return Clock.systemUTC(); }
}
