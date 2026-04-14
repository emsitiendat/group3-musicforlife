package com.example.musicforlife;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public interface ApiService {

    @GET("api/recommendations")
    Call<List<Song>> getRecommendations(@Query("page") int page, @Query("size") int size);

    @GET("/api/playlists/featured")
    Call<List<Playlist>> getFeaturedPlaylists();

    @GET("/api/playlists/{id}/songs")
    Call<List<Song>> getSongsInPlaylist(@Path("id") int playlistId);

    @GET("/api/songs/search")
    Call<List<Song>> searchSongs(@Query("q") String query, @Query("page") int page, @Query("limit") int limit);

    @POST("/api/login")
    Call<JsonObject> login(@Body HashMap<String, String> body);

    @POST("/api/register")
    Call<JsonObject> register(@Body HashMap<String, String> body);

    @POST("/api/favorite/toggle")
    Call<JsonObject> toggleFavorite(@Body HashMap<String, Object> body);

    @GET("/api/favorites")
    Call<List<Song>> getFavoriteSongs(@Query("username") String username);

    @GET("api/playlists/user/{username}")
    Call<List<Playlist>> getUserPlaylists(@Path("username") String username);

    @GET("api/playlists/user/{username}/artists")
    Call<List<Song>> getUserArtists(@Path("username") String username);

    @Multipart
    @POST("/api/user/playlists/create")
    Call<JsonObject> createPlaylist(
            @Part("username") RequestBody username,
            @Part("name") RequestBody name,
            @Part("description") RequestBody description,
            @Part("is_public") RequestBody isPublic,
            @Part MultipartBody.Part coverImage
    );

    @POST("/api/user/playlists/add_song")
    Call<JsonObject> addSongToPlaylist(@Body HashMap<String, Object> body);

    @GET("/api/songs/{id}/lyrics")
    Call<JsonObject> getSongLyrics(@Path("id") int songId);

    @POST("/api/history")
    Call<JsonObject> addListeningHistory(@Body HashMap<String, Object> body);

    @GET("/api/history/{username}")
    Call<List<Song>> getListeningHistory(@Path("username") String username);

    @DELETE("/api/playlists/{id}")
    Call<JsonObject> deletePlaylist(@Path("id") int playlistId);

    @DELETE("/api/playlists/{playlist_id}/songs/{song_id}")
    Call<JsonObject> removeSongFromPlaylist(@Path("playlist_id") int playlistId, @Path("song_id") int songId);

    @Multipart
    @POST("/api/playlists/{id}/update")
    Call<JsonObject> updatePlaylist(
            @Path("id") int playlistId,
            @Part("name") RequestBody name,
            @Part("description") RequestBody description,
            @Part("is_public") RequestBody isPublic,
            @Part MultipartBody.Part coverImage
    );

    @Streaming
    @GET
    Call<ResponseBody> downloadFile(@Url String fileUrl);

    @GET("/api/feed")
    Call<List<CommunityItem>> getCommunityFeed(@Query("username") String username);

    @GET("/api/v2/song/{song_id}/comments")
    Call<List<Comment>> getCommentsV2(@Path("song_id") int songId);

    @POST("/api/v2/song/comments/add")
    Call<JsonObject> addCommentV2(@Body JsonObject body);

    @GET("/api/artist/{artist_name}")
    Call<Artist> getArtistDetails(
            @Path("artist_name") String artistName,
            @Query("viewer") String viewerUsername
    );

    @GET("api/artists/unique_story")
    Call<List<Song>> getUniqueStoryArtists(@Query("limit") int limit);

    @POST("/api/playlists/{id}/order")
    Call<JsonObject> updatePlaylistOrder(@Path("id") int playlistId, @Body HashMap<String, Object> body);

    @GET("api/user/profile/{username}")
    Call<UserProfile> getUserProfile(
            @Path("username") String username,
            @Query("viewer") String viewerUsername
    );

    @POST("api/mobile/follow")
    Call<FollowResponse> toggleFollow(@Body Map<String, String> body);

    @POST("api/community/post")
    Call<SimpleResponse> createCommunityPost(@Body Map<String, Object> body);

    @Multipart
    @POST("api/story/upload")
    Call<SimpleResponse> uploadStory(
            @Part("username") RequestBody username,
            @Part MultipartBody.Part image
    );

    @GET("api/stories/feed")
    Call<List<StoryUser>> getStoriesFeed(@Query("username") String username);

    @GET("api/blend")
    Call<List<Song>> getBlendMix(@Query("user1") String myUsername, @Query("user2") String friendUsername);

    @Multipart
    @POST("api/mobile/user/update")
    Call<ProfileUpdateResponse> updateProfile(
            @Part("username") RequestBody username,
            @Part("display_name") RequestBody displayName,
            @Part("bio") RequestBody bio,
            @Part MultipartBody.Part avatar
    );

    @GET("api/user/{username}/followers")
    Call<List<User>> getUserFollowers(@Path("username") String username);

    @GET("api/user/{username}/following")
    Call<List<User>> getUserFollowing(@Path("username") String username);

    @POST("api/mobile/artist/follow")
    Call<FollowResponse> toggleArtistFollow(@Body Map<String, String> body);

    @GET("api/artist/{artist_name}/followers")
    Call<List<User>> getArtistFollowers(@Path("artist_name") String artistName);

    @GET("api/notifications/unread/{username}")
    Call<List<Notification>> getUnreadNotifications(@Path("username") String username);

    @GET("api/notifications/history/{username}")
    Call<List<Notification>> getNotificationHistory(@Path("username") String username);

    @POST("api/blend/create")
    Call<JsonObject> createBlendMix(@Body HashMap<String, Object> body);

    @GET("api/users/all")
    Call<com.google.gson.JsonArray> getAllUsers();
}