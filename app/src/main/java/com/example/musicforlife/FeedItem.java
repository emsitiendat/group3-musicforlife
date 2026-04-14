package com.example.musicforlife;

import java.util.List;

public class FeedItem {
    public static final int TYPE_SONG = 0;
    public static final int TYPE_PLAYLIST_CAROUSEL = 1;
    public static final int TYPE_ARTIST_SPOTLIGHT = 2;
    public static final int TYPE_CHART_CAROUSEL = 3;
    public static final int TYPE_LOADING = 4;

    private int type;

    private Song song;
    private List<Playlist> suggestedPlaylists;
    private List<Song> chartSongs;
    private Song spotlightArtistSong;

    public FeedItem(Song song) {
        this.type = TYPE_SONG;
        this.song = song;
    }

    public FeedItem(List<Playlist> playlists, int type) {
        this.type = type;
        this.suggestedPlaylists = playlists;
    }

    public FeedItem(int type, Song spotlightArtistSong) {
        this.type = type;
        this.spotlightArtistSong = spotlightArtistSong;
    }

    public FeedItem(int type, List<Song> chartSongs) {
        this.type = type;
        this.chartSongs = chartSongs;
    }

    public FeedItem(int type) {
        this.type = type;
    }

    public int getType() { return type; }
    public Song getSong() { return song; }
    public List<Playlist> getSuggestedPlaylists() { return suggestedPlaylists; }
    public Song getSpotlightArtistSong() { return spotlightArtistSong; }
    public List<Song> getChartSongs() { return chartSongs; }
}