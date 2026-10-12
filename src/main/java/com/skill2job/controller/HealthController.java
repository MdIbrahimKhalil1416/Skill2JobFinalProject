package com.skill2job.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@Tag(name = "Health")
public class HealthController {

    @GetMapping("/api/health")
    @Operation(summary = "Simple check that the app is running")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "app", "Skill2Job", "time", Instant.now().toString());
    }
}
