package com.example.musicforlife;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Song implements Serializable {
    @SerializedName("id")
    private int id;

    @SerializedName("title")
    private String title;

    @SerializedName("artist")
    private String artist;

    @SerializedName("file_path")
    private String filePath;

    @SerializedName("cover_art_path")
    private String coverArtPath;

    @SerializedName("duration")
    private int duration;
    private boolean isOffline = false;
    private String localFilePath = null;

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getFilePath() { return filePath; }
    public String getCoverArtPath() { return coverArtPath; }
    public int getDuration() { return duration; }

    public boolean isOffline() { return isOffline; }
    public String getLocalFilePath() { return localFilePath; }

    public void setId(int id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setArtist(String artist) { this.artist = artist; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public void setCoverArtPath(String coverArtPath) { this.coverArtPath = coverArtPath; }
    public void setDuration(int duration) { this.duration = duration; }

    public void setOffline(boolean offline) { isOffline = offline; }
    public void setLocalFilePath(String localFilePath) { this.localFilePath = localFilePath; }
}