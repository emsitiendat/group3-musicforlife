package com.example.musicforlife;

public class Mood {
    private String title;
    private String colorHex;

    public Mood(String title, String colorHex) {
        this.title = title;
        this.colorHex = colorHex;
    }

    public String getTitle() {
        return title;
    }

    public String getColorHex() {
        return colorHex;
    }
}