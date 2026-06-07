package com.martbot.repository;

import com.martbot.model.BotSession;
import com.martbot.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BotSessionRepository extends JpaRepository<BotSession, Long> {
    List<BotSession> findByStatus(SessionStatus status);
    List<BotSession> findBySessionNameContainingIgnoreCase(String name);
}
