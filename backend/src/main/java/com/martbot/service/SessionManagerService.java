package com.martbot.service;

import com.martbot.dto.SessionCreateRequest;
import com.martbot.model.BotSession;
import com.martbot.model.SessionStatus;
import com.martbot.repository.BotSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SessionManagerService {

    private static final Logger log = LoggerFactory.getLogger(SessionManagerService.class);

    private final BotSessionRepository sessionRepository;
    private final PlaywrightBotService botService;

    public SessionManagerService(BotSessionRepository sessionRepository, PlaywrightBotService botService) {
        this.sessionRepository = sessionRepository;
        this.botService = botService;
    }

    @Transactional
    public BotSession createAndLogin(SessionCreateRequest request) {
        BotSession session = new BotSession();
        session.setSessionName(request.getSessionName());
        session.setCraAccessToken(request.getCraAccessToken());
        session.setCraRefreshToken(request.getCraRefreshToken());
        session.setGa(request.getGa());
        session.setGaXgz(request.getGaXgz());
        session.setStatus(SessionStatus.LOGGING_IN);
        session = sessionRepository.save(session);

        // Perform login with Playwright
        Map<String, Object> loginResult = botService.loginWithCookies(session);

        if (Boolean.TRUE.equals(loginResult.get("success"))) {
            session.setStatus(SessionStatus.ACTIVE);
            session.setLastLoginAt(LocalDateTime.now());
            session.setSessionCookies((String) loginResult.get("cookies"));
            log.info("Session created and logged in: {}", session.getSessionName());
        } else {
            session.setStatus(SessionStatus.ERROR);
            log.error("Session login failed: {}", loginResult.get("error"));
        }

        return sessionRepository.save(session);
    }

    public Optional<BotSession> getSession(Long id) {
        return sessionRepository.findById(id);
    }

    public List<BotSession> getAllSessions() {
        return sessionRepository.findAll();
    }

    public List<BotSession> getActiveSessions() {
        return sessionRepository.findByStatus(SessionStatus.ACTIVE);
    }

    @Transactional
    public BotSession refreshSession(Long sessionId) {
        BotSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (botService.isSessionActive(sessionId)) {
            session.setStatus(SessionStatus.ACTIVE);
        } else {
            // Re-login
            session.setStatus(SessionStatus.LOGGING_IN);
            sessionRepository.save(session);

            Map<String, Object> loginResult = botService.loginWithCookies(session);
            if (Boolean.TRUE.equals(loginResult.get("success"))) {
                session.setStatus(SessionStatus.ACTIVE);
                session.setLastLoginAt(LocalDateTime.now());
                session.setSessionCookies((String) loginResult.get("cookies"));
            } else {
                session.setStatus(SessionStatus.EXPIRED);
            }
        }

        return sessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        botService.closeSession(sessionId);
        sessionRepository.deleteById(sessionId);
        log.info("Session deleted: {}", sessionId);
    }

    public boolean checkSessionHealth(Long sessionId) {
        return botService.isSessionActive(sessionId);
    }
}
