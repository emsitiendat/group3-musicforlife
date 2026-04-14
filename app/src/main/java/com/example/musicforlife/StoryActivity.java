package com.example.musicforlife;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import retrofit2.Call;

public class StoryActivity extends AppCompatActivity {

    private ImageView imgBackground, imgBgBlur;
    private ProgressBar progressBar;
    private TextView tvArtistName, tvSongPreview;
    private ImageButton btnClose;
    private Button btnViewProfile;

    private ProgressBar progressBarLoading;

    private List<Song> artistList = new ArrayList<>();
    private int currentIndex = 0;

    private ValueAnimator storyAnimator;
    private final long STORY_DURATION = 15000;

    private String currentImageUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story);

        imgBackground = findViewById(R.id.img_story_background);
        imgBgBlur = findViewById(R.id.img_story_bg_blur);
        progressBar = findViewById(R.id.progress_story);
        tvArtistName = findViewById(R.id.tv_story_artist);
        tvSongPreview = findViewById(R.id.tv_story_song_preview);
        btnClose = findViewById(R.id.btn_close_story);
        btnViewProfile = findViewById(R.id.btn_view_profile);
        progressBarLoading = findViewById(R.id.progress_bar_story_loading);

        List<Song> rawList = (List<Song>) getIntent().getSerializableExtra("EXTRA_ARTIST_LIST");

        if (rawList == null || rawList.isEmpty()) {
            finish();
            return;
        }

        int originalIndex = getIntent().getIntExtra("EXTRA_CURRENT_INDEX", 0);
        String targetArtistName = "";

        if (originalIndex >= 0 && originalIndex < rawList.size()) {
            targetArtistName = rawList.get(originalIndex).getArtist();
            if (targetArtistName != null) {
                targetArtistName = targetArtistName.trim().toLowerCase();
            }
        }

        HashSet<String> seenArtists = new HashSet<>();
        artistList.clear();

        for (Song s : rawList) {
            String aName = s.getArtist();
            if (aName != null && !aName.trim().isEmpty()) {
                String normalizedName = aName.trim().toLowerCase();
                if (!seenArtists.contains(normalizedName)) {
                    seenArtists.add(normalizedName);
                    artistList.add(s);
                }
            }
        }

        currentIndex = 0;
        if (targetArtistName != null && !targetArtistName.isEmpty()) {
            for (int i = 0; i < artistList.size(); i++) {
                String currentName = artistList.get(i).getArtist();
                if (currentName != null && currentName.trim().toLowerCase().equals(targetArtistName)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        setupGesturesAndClicks();
        loadStoryAtIndex(currentIndex);
    }

    private void setupGesturesAndClicks() {
        btnClose.setOnClickListener(v -> finish());

        btnViewProfile.setOnClickListener(v -> {
            if (currentIndex >= 0 && currentIndex < artistList.size()) {
                String artistName = artistList.get(currentIndex).getArtist();
                Intent intent = new Intent(StoryActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("open_artist_detail", true);
                intent.putExtra("artist_name", artistName);
                startActivity(intent);
                finish();
            }
        });

        tvArtistName.setOnClickListener(v -> btnViewProfile.performClick());

        View root = findViewById(R.id.layout_story_root);
        root.setOnTouchListener(new OnSwipeTouchListener(this) {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (storyAnimator != null) storyAnimator.pause();
                        sendMusicAction(MusicService.ACTION_PAUSE);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (storyAnimator != null) storyAnimator.resume();
                        sendMusicAction(MusicService.ACTION_RESUME);
                        break;
                }
                return super.onTouch(v, event);
            }

            @Override
            public void onSwipeLeft() {
                if (currentIndex < artistList.size() - 1) {
                    currentIndex++;
                    loadStoryAtIndex(currentIndex);
                } else {
                    finish();
                }
            }

            @Override
            public void onSwipeRight() {
                if (currentIndex > 0) {
                    currentIndex--;
                    loadStoryAtIndex(currentIndex);
                }
            }
        });
    }

    private void loadStoryAtIndex(int index) {
        if (storyAnimator != null) storyAnimator.cancel();
        progressBar.setProgress(0);

        Song songContext = artistList.get(index);
        tvArtistName.setText(songContext.getArtist());
        tvSongPreview.setText("Đang tìm bài hát...");

        syncLoadAndDisplayImages(songContext.getCoverArtPath());
        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String myUsername = prefs.getString("userUsername", "");
        Call<Artist> call = RetrofitClient.getApiService().getArtistDetails(songContext.getArtist(), myUsername);
        ApiHelper.request(call, progressBarLoading, null, new ApiHelper.CallbackResult<Artist>() {
            @Override
            public void onSuccess(Artist result) {
                if (!isDestroyed() && result != null && result.success && result.artist != null) {

                    String imgUrl = result.artist.bannerImageUrl != null ?
                            result.artist.bannerImageUrl : result.artist.profileImageUrl;

                    syncLoadAndDisplayImages(imgUrl);

                    if (result.songs != null && !result.songs.isEmpty()) {
                        List<Song> artistFullSongs = result.songs;
                        int randomIdx = new Random().nextInt(artistFullSongs.size());
                        Song randomTopSong = artistFullSongs.get(randomIdx);

                        tvSongPreview.setText("Đang nghe điệp khúc: " + randomTopSong.getTitle());

                        Intent playIntent = new Intent(StoryActivity.this, MusicService.class);
                        playIntent.setAction(MusicService.ACTION_PLAY);
                        playIntent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(artistFullSongs));
                        playIntent.putExtra("EXTRA_SONG_POSITION", randomIdx);
                        playIntent.putExtra("EXTRA_START_POSITION", 45000);
                        startService(playIntent);
                    } else {
                        tvSongPreview.setText("Chưa có bài hát nào");
                    }

                    startStoryTimer();

                } else if (!isDestroyed()) {
                    Toast.makeText(StoryActivity.this, "Không tìm thấy thông tin", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (!isDestroyed()) {
                    Toast.makeText(StoryActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        });
    }

    private void syncLoadAndDisplayImages(String url) {
        if (url == null || url.trim().isEmpty()) return;

        final String requestUrl = Utils.normalizeUrl(url);
        currentImageUrl = requestUrl;

        Glide.with(this)
                .asBitmap()
                .load(requestUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {

                        if (!requestUrl.equals(currentImageUrl) || isDestroyed()) return;

                        new Thread(() -> {
                            try {
                                int width = Math.max(1, Math.round(resource.getWidth() * 0.3f));
                                int height = Math.max(1, Math.round(resource.getHeight() * 0.3f));
                                Bitmap scaledForBlur = Bitmap.createScaledBitmap(resource, width, height, false);

                                Bitmap blurredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                                RenderScript rs = RenderScript.create(getApplicationContext());
                                ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
                                Allocation tmpIn = Allocation.createFromBitmap(rs, scaledForBlur);
                                Allocation tmpOut = Allocation.createFromBitmap(rs, blurredBitmap);

                                blurScript.setRadius(25f);
                                blurScript.setInput(tmpIn);
                                blurScript.forEach(tmpOut);
                                tmpOut.copyTo(blurredBitmap);

                                tmpIn.destroy();
                                tmpOut.destroy();
                                blurScript.destroy();
                                rs.destroy();

                                runOnUiThread(() -> {
                                    if (!requestUrl.equals(currentImageUrl) || isDestroyed()) return;

                                    imgBackground.setImageBitmap(resource);
                                    imgBgBlur.setImageBitmap(blurredBitmap);

                                    imgBackground.setAlpha(0.5f);
                                    imgBgBlur.setAlpha(0.5f);
                                    imgBackground.animate().alpha(1f).setDuration(400).start();
                                    imgBgBlur.animate().alpha(1f).setDuration(400).start();
                                });

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
    }

    private void startStoryTimer() {
        storyAnimator = ValueAnimator.ofInt(0, 1000);
        storyAnimator.setDuration(STORY_DURATION);
        storyAnimator.setInterpolator(new LinearInterpolator());
        storyAnimator.addUpdateListener(animation -> {
            progressBar.setProgress((int) animation.getAnimatedValue());
            if ((int) animation.getAnimatedValue() == 1000) {
                if (currentIndex < artistList.size() - 1) {
                    currentIndex++;
                    loadStoryAtIndex(currentIndex);
                } else {
                    finish();
                }
            }
        });
        storyAnimator.start();
    }

    private void sendMusicAction(String action) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        startService(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (storyAnimator != null) storyAnimator.cancel();
    }
}