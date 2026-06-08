package com.martbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bot_sessions")
public class BotSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionName;

    @Column(length = 2048)
    private String craAccessToken;

    @Column(length = 2048)
    private String craRefreshToken;

    @Column(length = 512)
    private String ga;

    @Column(length = 512)
    private String gaXgz;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.INACTIVE;

    @Column(length = 4096)
    private String sessionCookies;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public String getCraAccessToken() {
        return craAccessToken;
    }

    public void setCraAccessToken(String craAccessToken) {
        this.craAccessToken = craAccessToken;
    }

    public String getCraRefreshToken() {
        return craRefreshToken;
    }

    public void setCraRefreshToken(String craRefreshToken) {
        this.craRefreshToken = craRefreshToken;
    }

    public String getGa() {
        return ga;
    }

    public void setGa(String ga) {
        this.ga = ga;
    }

    public String getGaXgz() {
        return gaXgz;
    }

    public void setGaXgz(String gaXgz) {
        this.gaXgz = gaXgz;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getSessionCookies() {
        return sessionCookies;
    }

    public void setSessionCookies(String sessionCookies) {
        this.sessionCookies = sessionCookies;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
