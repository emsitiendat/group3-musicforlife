package com.example.musicforlife;

public class Category {
    private int id;
    private String name;
    private String hexColor;
    private int imageResId;

    public Category(int id, String name, String hexColor, int imageResId) {
        this.id = id;
        this.name = name;
        this.hexColor = hexColor;
        this.imageResId = imageResId;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getHexColor() { return hexColor; }
    public int getImageResId() { return imageResId; }
}
