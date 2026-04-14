package com.example.musicforlife;

public class FriendActivity {
    private String friendName;
    private String avatarUrl;
    private Song currentSong;

    public FriendActivity(String friendName, String avatarUrl, Song currentSong) {
        this.friendName = friendName;
        this.avatarUrl = avatarUrl;
        this.currentSong = currentSong;
    }

    public String getFriendName() {
        return friendName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Song getCurrentSong() {
        return currentSong;
    }
}