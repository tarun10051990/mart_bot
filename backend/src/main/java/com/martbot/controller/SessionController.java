package com.martbot.controller;

import com.martbot.dto.BotResponse;
import com.martbot.dto.SessionCreateRequest;
import com.martbot.model.BotSession;
import com.martbot.service.PlaywrightBotService;
import com.martbot.service.SessionManagerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionManagerService sessionManager;
    private final PlaywrightBotService botService;

    public SessionController(SessionManagerService sessionManager, PlaywrightBotService botService) {
        this.sessionManager = sessionManager;
        this.botService = botService;
    }

    @PostMapping
    public ResponseEntity<BotResponse<BotSession>> createSession(@Valid @RequestBody SessionCreateRequest request) {
        try {
            BotSession session = sessionManager.createAndLogin(request);
            return ResponseEntity.ok(BotResponse.success("Session created and login attempted", session));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(BotResponse.error("Failed to create session: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<BotResponse<List<BotSession>>> getAllSessions() {
        List<BotSession> sessions = sessionManager.getAllSessions();
        return ResponseEntity.ok(BotResponse.success("Sessions retrieved", sessions));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BotResponse<BotSession>> getSession(@PathVariable Long id) {
        return sessionManager.getSession(id)
                .map(s -> ResponseEntity.ok(BotResponse.success("Session found", s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    public ResponseEntity<BotResponse<List<BotSession>>> getActiveSessions() {
        List<BotSession> sessions = sessionManager.getActiveSessions();
        return ResponseEntity.ok(BotResponse.success("Active sessions retrieved", sessions));
    }

    @PostMapping("/{id}/refresh")
    public ResponseEntity<BotResponse<BotSession>> refreshSession(@PathVariable Long id) {
        try {
            BotSession session = sessionManager.refreshSession(id);
            return ResponseEntity.ok(BotResponse.success("Session refreshed", session));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(BotResponse.error("Failed to refresh: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/health")
    public ResponseEntity<BotResponse<Boolean>> checkHealth(@PathVariable Long id) {
        boolean active = sessionManager.checkSessionHealth(id);
        return ResponseEntity.ok(BotResponse.success(active ? "Session is active" : "Session is inactive", active));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BotResponse<Void>> deleteSession(@PathVariable Long id) {
        sessionManager.deleteSession(id);
        return ResponseEntity.ok(BotResponse.success("Session deleted"));
    }

    @GetMapping("/{id}/addresses")
    public ResponseEntity<BotResponse<List<Map<String, String>>>> getAddresses(@PathVariable Long id) {
        return sessionManager.getSession(id)
                .map(session -> {
                    List<Map<String, String>> addresses = botService.fetchAddresses(session);
                    return ResponseEntity.ok(BotResponse.success("Addresses retrieved", addresses));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
