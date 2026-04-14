package com.example.musicforlife;

public class ProfileUpdateResponse {
    private boolean success;
    private String message;
    private String avatar_url;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getAvatarUrl() { return avatar_url; }
}