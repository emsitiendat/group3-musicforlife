package com.example.musicforlife;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;

public class OtherUserProfileActivity extends AppCompatActivity {

    private ImageView imgAvatar;
    private TextView tvDisplayName, tvUsername, tvFollowers, tvFollowing;
    private RecyclerView rvOtherTopSongs;
    private TextView tvNoMusicData;
    private GridSongAdapter gridSongAdapter;
    private Button btnFollow;
    private ImageButton btnBack;

    private String targetUsername;
    private String myUsername;

    private ConstraintLayout mainRootLayout;
    private int currentBgColor;
    private Song currentBackgroundSong;

    private final BroadcastReceiver musicReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MusicService.ACTION_UPDATE_UI.equals(intent.getAction())) {
                Song song = (Song) intent.getSerializableExtra("EXTRA_SONG");
                if (song != null) {
                    updateDynamicBackground(song);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other_user_profile);

        mainRootLayout = findViewById(R.id.root_other_profile);
        currentBgColor = ContextCompat.getColor(this, R.color.bg_main);

        imgAvatar = findViewById(R.id.img_other_avatar);
        tvDisplayName = findViewById(R.id.tv_other_display_name);
        tvUsername = findViewById(R.id.tv_other_username);
        tvFollowers = findViewById(R.id.tv_other_followers);
        tvFollowing = findViewById(R.id.tv_other_following);
        btnFollow = findViewById(R.id.btn_follow_user);
        rvOtherTopSongs = findViewById(R.id.rv_other_top_songs);
        tvNoMusicData = findViewById(R.id.tv_no_music_data);

        rvOtherTopSongs.setLayoutManager(new GridLayoutManager(this, 3));
        btnBack = findViewById(R.id.btn_back_profile);

        btnBack.setOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        myUsername = prefs.getString("userUsername", "");

        targetUsername = getIntent().getStringExtra("TARGET_USERNAME");

        if (targetUsername == null || targetUsername.isEmpty()) {
            Toast.makeText(this, "Lỗi dữ liệu", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (targetUsername.equals(myUsername)) {
            btnFollow.setVisibility(View.GONE);
        }

        btnFollow.setOnClickListener(v -> toggleFollow());

        loadUserProfile();
        loadOtherUserMusic();

        if (MusicService.globalCurrentSong != null) {
            updateDynamicBackground(MusicService.globalCurrentSong);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(MusicService.ACTION_UPDATE_UI);
        ContextCompat.registerReceiver(this, musicReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (MusicService.globalCurrentSong != null) {
            updateDynamicBackground(MusicService.globalCurrentSong);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(musicReceiver);
        } catch (IllegalArgumentException ignored) {}
    }

    private void updateDynamicBackground(Song song) {
        if (song == null || mainRootLayout == null) return;

        if (currentBackgroundSong != null && currentBackgroundSong.getId() == song.getId()) return;
        currentBackgroundSong = song;

        GlideHelper.loadBitmap(this, song.getCoverArtPath(), new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                new Thread(() -> {
                    Palette palette = Palette.from(resource).generate();
                    int baseColor = palette.getVibrantColor(palette.getDominantColor(Color.parseColor("#1A1A1A")));

                    float[] hsvAppBg = new float[3];
                    Color.colorToHSV(baseColor, hsvAppBg);
                    hsvAppBg[1] = Math.min(1.0f, hsvAppBg[1] * 1.2f);
                    hsvAppBg[2] = 0.25f;
                    int targetAppColor = Color.HSVToColor(hsvAppBg);

                    new Handler(Looper.getMainLooper()).post(() -> animateAppBackground(targetAppColor));
                }).start();
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {}
        });
    }

    private void animateAppBackground(int targetColor) {
        if (mainRootLayout == null) return;

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), currentBgColor, targetColor);
        colorAnimation.setDuration(800);
        colorAnimation.addUpdateListener(animator -> {
            int animatedValue = (int) animator.getAnimatedValue();
            mainRootLayout.setBackgroundColor(animatedValue);

            if (getWindow() != null) {
                getWindow().setStatusBarColor(animatedValue);
            }
        });
        colorAnimation.start();
        currentBgColor = targetColor;
    }

    private void loadUserProfile() {
        Call<UserProfile> call = RetrofitClient.getApiService().getUserProfile(targetUsername, myUsername);

        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                if (result != null) {
                    tvDisplayName.setText(result.getDisplayName());
                    tvUsername.setText("@" + result.getUsername());
                    tvFollowers.setText(String.valueOf(result.getFollowersCount()));
                    tvFollowing.setText(String.valueOf(result.getFollowingCount()));

                    String avatarUrl = (result.getAvatarUrl() != null) ? result.getAvatarUrl() : "";
                    GlideHelper.loadCircleForAdapter(OtherUserProfileActivity.this, avatarUrl, imgAvatar);

                    updateFollowButtonUI(result.isFollowing());
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(OtherUserProfileActivity.this, "Không thể tải hồ sơ: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleFollow() {
        if (myUsername.isEmpty()) {
            Toast.makeText(this, "Bạn cần đăng nhập để theo dõi", Toast.LENGTH_SHORT).show();
            return;
        }

        btnFollow.setEnabled(false);

        Map<String, String> body = new HashMap<>();
        body.put("follower_username", myUsername);
        body.put("followed_username", targetUsername);

        Call<FollowResponse> call = RetrofitClient.getApiService().toggleFollow(body);
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<FollowResponse>() {
            @Override
            public void onSuccess(FollowResponse result) {
                btnFollow.setEnabled(true);

                if (result != null && result.isSuccess()) {
                    tvFollowers.setText(String.valueOf(result.getFollowersCount()));
                    updateFollowButtonUI(result.isFollowing());
                    Toast.makeText(OtherUserProfileActivity.this, result.getMessage(), Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent("ACTION_FOLLOW_STATUS_CHANGED");
                    intent.setPackage(getPackageName());
                    sendBroadcast(intent);
                } else {
                    Toast.makeText(OtherUserProfileActivity.this, result != null ? result.getMessage() : "Lỗi không xác định", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                btnFollow.setEnabled(true);
                Toast.makeText(OtherUserProfileActivity.this, "Lỗi: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void updateFollowButtonUI(boolean isFollowing) {
        if (isFollowing) {
            btnFollow.setText("Đang theo dõi");
            btnFollow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.bg_surface)));
            btnFollow.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        } else {
            btnFollow.setText("Theo dõi");
            btnFollow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            btnFollow.setTextColor(Color.parseColor("#121212"));
        }
    }

    private void loadOtherUserMusic() {
        Call<List<Song>> call = RetrofitClient.getApiService().getListeningHistory(targetUsername);

        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                if (result != null && !result.isEmpty()) {
                    tvNoMusicData.setVisibility(View.GONE);
                    rvOtherTopSongs.setVisibility(View.VISIBLE);

                    List<Song> top9Songs = result.size() > 9 ? result.subList(0, 9) : result;

                    gridSongAdapter = new GridSongAdapter(OtherUserProfileActivity.this, top9Songs);
                    rvOtherTopSongs.setAdapter(gridSongAdapter);
                } else {
                    tvNoMusicData.setVisibility(View.VISIBLE);
                    rvOtherTopSongs.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String errorMessage) {
                tvNoMusicData.setVisibility(View.VISIBLE);
                rvOtherTopSongs.setVisibility(View.GONE);
            }
        });
    }
}