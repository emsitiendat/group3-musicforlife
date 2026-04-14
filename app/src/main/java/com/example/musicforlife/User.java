package com.example.musicforlife;

import com.google.gson.annotations.SerializedName;

public class User {
    private int id;
    private String username;

    @SerializedName("display_name")
    private String display_name;

    @SerializedName("avatar_url")
    private String avatar_url;

    private String type;

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return display_name;
    }

    public String getAvatarUrl() {
        return avatar_url;
    }

    public String getType() {
        return type;
    }
}