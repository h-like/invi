package com.invi.api.common;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public HelloResponse hello() {
        return new HelloResponse("invi-api is alive", Instant.now());
    }

    public record HelloResponse(String message, Instant serverTime) {
    }
}
