package com.martbot.dto;

public class BotResponse<T> {

    private boolean success;
    private String message;
    private T data;

    public BotResponse() {}

    public static <T> BotResponse<T> success(String message, T data) {
        BotResponse<T> response = new BotResponse<>();
        response.success = true;
        response.message = message;
        response.data = data;
        return response;
    }

    public static <T> BotResponse<T> success(String message) {
        BotResponse<T> response = new BotResponse<>();
        response.success = true;
        response.message = message;
        return response;
    }

    public static <T> BotResponse<T> error(String message) {
        BotResponse<T> response = new BotResponse<>();
        response.success = false;
        response.message = message;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
