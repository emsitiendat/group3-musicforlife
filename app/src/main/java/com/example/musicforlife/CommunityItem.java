package com.example.musicforlife;

public class CommunityItem {
    private String id;
    private String username;
    private String avatarUrl;
    private String coverUrl;
    private String caption;
    private String timeAgo;
    private int likeCount;
    private int commentCount;

    public CommunityItem() {
    }

    public CommunityItem(String id, String username, String avatarUrl, String coverUrl, String caption, String timeAgo, int likeCount, int commentCount) {
        this.id = id;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.coverUrl = coverUrl;
        this.caption = caption;
        this.timeAgo = timeAgo;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getCoverUrl() { return coverUrl; }
    public String getCaption() { return caption; }
    public String getTimeAgo() { return timeAgo; }
    public int getLikeCount() { return likeCount; }
    public int getCommentCount() { return commentCount; }
}