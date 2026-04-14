package com.example.musicforlife;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Artist {
    @SerializedName("success")
    public boolean success;

    @SerializedName("artist")
    public ArtistInfo artist;

    @SerializedName("songs")
    public List<Song> songs;

    public static class ArtistInfo {
        @SerializedName("name")
        public String name;

        @SerializedName("bio")
        public String bio;

        @SerializedName("profile_image_url")
        public String profileImageUrl;

        @SerializedName("banner_image_url")
        public String bannerImageUrl;

        @SerializedName("followers_count")
        public int followersCount;

        @SerializedName("is_following")
        public boolean is_following;
    }
}