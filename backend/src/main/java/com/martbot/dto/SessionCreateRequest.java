package com.martbot.dto;

import jakarta.validation.constraints.NotBlank;

public class SessionCreateRequest {

    @NotBlank(message = "Session name is required")
    private String sessionName;

    @NotBlank(message = "CRA access token is required")
    private String craAccessToken;

    @NotBlank(message = "CRA refresh token is required")
    private String craRefreshToken;

    private String ga;

    private String gaXgz;

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
}
