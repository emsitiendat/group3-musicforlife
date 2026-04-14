package com.example.musicforlife;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

/**
 * Model Playlist tích hợp logic đồng bộ ảnh bìa mới nhất.
 * Giải quyết các vấn đề về hiển thị Collage, ảnh bài hát mới nhất và phá Cache Glide.
 * (Bổ sung thêm các trường dữ liệu hỗ trợ tính năng Blend Mix)
 */
public class Playlist implements Serializable {
    private int id;
    private String name;
    private int plays;
    private int likes;
    private String description;

    @SerializedName(value = "cover_url", alternate = {"custom_cover_path"})
    private String coverArtPath;

    @SerializedName("is_custom_cover")
    private boolean isCustomCover;

    @SerializedName("song_count")
    private int songCount;

    @SerializedName("collage_covers")
    private List<String> collageCovers;

    @SerializedName("updated_at")
    private long updatedAt;

    @SerializedName("is_blend")
    private boolean isBlend;

    @SerializedName("owner_avatar")
    private String ownerAvatar;

    @SerializedName("partner_avatar")
    private String partnerAvatar;

    @SerializedName("partner_username")
    private String partnerUsername;

    @SerializedName("owner_username")
    private String ownerUsername;



    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCoverArtPath() { return coverArtPath; }
    public void setCoverArtPath(String coverArtPath) { this.coverArtPath = coverArtPath; }

    public int getPlays() { return plays; }
    public void setPlays(int plays) { this.plays = plays; }

    public int getLikes() { return likes; }
    public void setLikes(int likes) { this.likes = likes; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isCustomCover() { return isCustomCover; }
    public void setCustomCover(boolean customCover) { isCustomCover = customCover; }

    public int getSongCount() { return songCount; }
    public void setSongCount(int songCount) { this.songCount = songCount; }

    public List<String> getCollageCovers() { return collageCovers; }
    public void setCollageCovers(List<String> collageCovers) { this.collageCovers = collageCovers; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public boolean isBlend() { return isBlend; }
    public void setBlend(boolean blend) { isBlend = blend; }

    public String getOwnerAvatar() { return ownerAvatar; }
    public void setOwnerAvatar(String ownerAvatar) { this.ownerAvatar = ownerAvatar; }

    public String getPartnerAvatar() { return partnerAvatar; }
    public void setPartnerAvatar(String partnerAvatar) { this.partnerAvatar = partnerAvatar; }

    public String getPartnerUsername() { return partnerUsername; }
    public void setPartnerUsername(String partnerUsername) { this.partnerUsername = partnerUsername; }

    public String getOwnerUsername() { return ownerUsername; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
}