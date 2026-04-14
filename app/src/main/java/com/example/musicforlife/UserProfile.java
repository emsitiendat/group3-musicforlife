package com.example.musicforlife;

import com.google.gson.annotations.SerializedName;

public class UserProfile {
    private String username;

    @SerializedName("display_name")
    private String display_name;

    @SerializedName("avatar_url")
    private String avatar_url;

    @SerializedName("followers_count")
    private int followers_count;

    @SerializedName("following_count")
    private int following_count;

    @SerializedName("is_following")
    private boolean is_following;

    public String getUsername() { return username; }
    public String getDisplayName() { return display_name; }
    public String getAvatarUrl() { return avatar_url; }
    public int getFollowersCount() { return followers_count; }
    public int getFollowingCount() { return following_count; }
    public boolean isFollowing() { return is_following; }
}