package com.invi.api.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hibernate's JSON column mapping (@JdbcTypeCode(SqlTypes.JSON)) resolves against
 * Jackson 2, even though Spring Boot 4's HTTP layer moved to Jackson 3
 * (tools.jackson.databind, via spring-boot-starter-jackson). So entities keep
 * com.fasterxml.jackson.databind.JsonNode, and service code bridges to/from
 * Jackson 3 at the DTO edge via JsonNode#toString() — see InvitationService.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper entityJsonMapper() {
        return new ObjectMapper();
    }
}
