package com.invi.api.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4's webmvc starter no longer auto-wires an ObjectMapper the way
 * spring-boot-starter-web used to — declared explicitly so both HTTP responses and
 * anything that @Autowires ObjectMapper directly (e.g. TemplateSeeder) get one with
 * JSR-310 support (Instant/LocalDate as ISO-8601, not arrays).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
