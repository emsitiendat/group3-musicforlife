package com.example.musicforlife;

import com.google.gson.annotations.SerializedName;

public class Comment {

    @SerializedName("id")
    private int id;

    @SerializedName("text")
    private String text;

    @SerializedName("timestamp")
    private String timestamp;

    @SerializedName("user")
    private UserInfo user;

    private boolean isOptimistic = false;

    public Comment() {
    }

    public Comment(String text, UserInfo user, boolean isOptimistic) {
        this.text = text;
        this.user = user;
        this.isOptimistic = isOptimistic;
    }

    public static class UserInfo {
        @SerializedName("username")
        public String username;

        @SerializedName("display_name")
        public String name_display;

        @SerializedName("avatar_url")
        public String avatar_url;

        public UserInfo() {
        }

        public UserInfo(String name_display, String avatar_url) {
            this.name_display = name_display;
            this.avatar_url = avatar_url;
        }
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }

    public boolean isOptimistic() {
        return isOptimistic;
    }

    public void setOptimistic(boolean optimistic) {
        isOptimistic = optimistic;
    }
}