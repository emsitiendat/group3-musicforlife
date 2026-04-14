package com.example.musicforlife;

public class FollowResponse {
    private boolean success;
    private String message;
    private boolean is_following;
    private int followers_count;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public boolean isFollowing() { return is_following; }
    public int getFollowersCount() { return followers_count; }
}