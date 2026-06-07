package com.martbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bot")
public class BotConfig {

    private String loginUrl;
    private String jiomartBaseUrl;
    private boolean headless = true;
    private int timeoutMs = 30000;

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public String getJiomartBaseUrl() {
        return jiomartBaseUrl;
    }

    public void setJiomartBaseUrl(String jiomartBaseUrl) {
        this.jiomartBaseUrl = jiomartBaseUrl;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
