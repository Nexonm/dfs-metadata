package dev.nexonm.distfs.metadata.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {

    @GetMapping("/check")
    public ResponseEntity<String> health() {
        log.info("Health check requested");
        return ResponseEntity.ok("Healthy!");
    }
}
