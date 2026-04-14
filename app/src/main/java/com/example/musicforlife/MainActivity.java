package com.example.musicforlife;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private ConstraintLayout mainRootLayout;
    private int currentBgColor;

    private BottomNavigationView bottomNavigationView;
    private ConstraintLayout miniPlayerContainer;
    private TextView tvMiniTitle, tvMiniArtist;
    private ImageView imgMiniCover;
    private ImageButton btnMiniPlay;
    private ProgressBar progressMiniPlayer;

    private ImageButton btnMiniHeart;

    private boolean doubleBackToExitPressedOnce = false;
    private boolean isPlaying = false;
    private Song currentSong;

    private boolean isMiniPlayerHiddenByUser = false;
    private boolean isHiddenForDiscover = false;
    private GestureDetector gestureDetector;

    private static final HashMap<Integer, Integer> miniPlayerColorCache = new HashMap<>();
    private static final HashMap<Integer, Integer> appBgColorCache = new HashMap<>();

    private long lastClickTime = 0;
    private long lastBottomNavClickTime = 0;

    private Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;
    private final int POLLING_INTERVAL = 15000;
    private static final String CHANNEL_ID = "MFL_NOTIFICATIONS";

    private boolean isSafeClick() {
        long now = System.currentTimeMillis();
        if (now - lastClickTime > 400) {
            lastClickTime = now;
            return true;
        }
        return false;
    }

    private Fragment fragmentHome;
    private Fragment fragmentSearch;
    private Fragment fragmentCommunity;
    private Fragment fragmentPlaylists;
    private Fragment fragmentAccount;

    private Fragment activeFragment;

    private final BroadcastReceiver musicReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (MusicService.ACTION_UPDATE_UI.equals(action)) {
                isMiniPlayerHiddenByUser = false;
                Song song = (Song) intent.getSerializableExtra("EXTRA_SONG");

                if (song != null) {
                    MusicService.globalCurrentSong = song;
                }

                boolean playing = intent.getBooleanExtra("EXTRA_IS_PLAYING", false);
                renderState(song, playing);
            }
            else if (MusicService.ACTION_UPDATE_PROGRESS.equals(action)) {
                int currentPosition = intent.getIntExtra("EXTRA_CURRENT_POSITION", 0);
                int totalDuration = intent.getIntExtra("EXTRA_TOTAL_DURATION", 0);
                updateProgressUI(currentPosition, totalDuration);
            }
            else if ("com.example.musicforlife.ACTION_FAVORITE_CHANGED".equals(action)) {
                if (currentSong != null) {
                    updateMiniHeartUI(currentSong.getId());
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        createNotificationChannel();

        mainRootLayout = findViewById(R.id.main_root_layout);
        currentBgColor = ContextCompat.getColor(this, R.color.bg_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        miniPlayerContainer = findViewById(R.id.mini_player_container);
        tvMiniTitle = findViewById(R.id.tv_mini_title);
        tvMiniArtist = findViewById(R.id.tv_mini_artist);
        imgMiniCover = findViewById(R.id.img_mini_cover);
        btnMiniPlay = findViewById(R.id.btn_mini_play);
        progressMiniPlayer = findViewById(R.id.progress_mini_player);
        btnMiniHeart = findViewById(R.id.btn_mini_heart);

        tvMiniTitle.setSelected(true);

        setupGestureDetection();

        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            int baseMarginPx = (int) (24 * getResources().getDisplayMetrics().density);
            mlp.bottomMargin = baseMarginPx + insets.bottom;
            v.setLayoutParams(mlp);
            return windowInsets;
        });

        if (savedInstanceState == null) {
            fragmentHome = new HomeFragment();
            fragmentSearch = new SearchFragment();
            fragmentCommunity = new CommunityFragment();
            fragmentPlaylists = new PlaylistsFragment();
            fragmentAccount = new AccountFragment();
            activeFragment = fragmentHome;

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, fragmentAccount, "account").hide(fragmentAccount)
                    .add(R.id.fragment_container, fragmentPlaylists, "playlists").hide(fragmentPlaylists)
                    .add(R.id.fragment_container, fragmentCommunity, "community").hide(fragmentCommunity)
                    .add(R.id.fragment_container, fragmentSearch, "search").hide(fragmentSearch)
                    .add(R.id.fragment_container, fragmentHome, "home").commit();
        } else {
            fragmentHome = getSupportFragmentManager().findFragmentByTag("home");
            if (fragmentHome == null) fragmentHome = new HomeFragment();

            fragmentSearch = getSupportFragmentManager().findFragmentByTag("search");
            if (fragmentSearch == null) fragmentSearch = new SearchFragment();

            fragmentCommunity = getSupportFragmentManager().findFragmentByTag("community");
            if (fragmentCommunity == null) fragmentCommunity = new CommunityFragment();

            fragmentPlaylists = getSupportFragmentManager().findFragmentByTag("playlists");
            if (fragmentPlaylists == null) fragmentPlaylists = new PlaylistsFragment();

            fragmentAccount = getSupportFragmentManager().findFragmentByTag("account");
            if (fragmentAccount == null) fragmentAccount = new AccountFragment();

            activeFragment = fragmentHome;
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment != null && fragment.isAdded() && !fragment.isHidden()) {
                    activeFragment = fragment;
                    break;
                }
            }
        }

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment current = null;
            List<Fragment> fragments = getSupportFragmentManager().getFragments();
            for (int i = fragments.size() - 1; i >= 0; i--) {
                Fragment f = fragments.get(i);
                if (f != null && f.isAdded() && !f.isHidden()) {
                    current = f;
                    break;
                }
            }
            if (current != null) {
                updateUIVisibility(current);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStackImmediate();
                } else if (bottomNavigationView.getSelectedItemId() != R.id.nav_home) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_home);
                } else {
                    if (doubleBackToExitPressedOnce) {
                        this.setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                        return;
                    }
                    doubleBackToExitPressedOnce = true;
                    Toast.makeText(MainActivity.this, "Nhấn lần nữa để thoát ứng dụng", Toast.LENGTH_SHORT).show();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
                }
            }
        });

        bottomNavigationView.setOnItemSelectedListener(item -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBottomNavClickTime < 300) {
                return false;
            }
            lastBottomNavClickTime = currentTime;

            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }

            int itemId = item.getItemId();
            Fragment targetFragment = fragmentHome;
            String tag = "home";

            if (itemId == R.id.nav_home) { targetFragment = fragmentHome; tag = "home"; }
            else if (itemId == R.id.nav_search) { targetFragment = fragmentSearch; tag = "search"; }
            else if (itemId == R.id.nav_community) { targetFragment = fragmentCommunity; tag = "community"; }
            else if (itemId == R.id.nav_playlists) { targetFragment = fragmentPlaylists; tag = "playlists"; }
            else if (itemId == R.id.nav_account) { targetFragment = fragmentAccount; tag = "account"; }

            if (targetFragment == activeFragment) {
                return true;
            }

            androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            if (activeFragment != null && activeFragment.isAdded()) {
                transaction.hide(activeFragment);
            }

            if (!targetFragment.isAdded()) {
                transaction.add(R.id.fragment_container, targetFragment, tag);
            } else {
                transaction.show(targetFragment);
            }

            transaction.commit();
            activeFragment = targetFragment;

            updateUIVisibility(activeFragment);
            return true;
        });

        bottomNavigationView.setOnItemReselectedListener(item -> {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
            else if (activeFragment instanceof ScrollToTopListener) {
                ((ScrollToTopListener) activeFragment).scrollToTop();
            }
        });

        if (savedInstanceState == null) bottomNavigationView.setSelectedItemId(R.id.nav_home);

        miniPlayerContainer.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        btnMiniPlay.setOnClickListener(v -> {
            if (!isSafeClick()) return;
            Intent intent = new Intent(MainActivity.this, MusicService.class);
            intent.setAction(isPlaying ? MusicService.ACTION_PAUSE : MusicService.ACTION_RESUME);
            startService(intent);
        });

        if (btnMiniHeart != null) {
            btnMiniHeart.setOnClickListener(v -> {
                if (isSafeClick()) toggleFavoriteMiniPlayer();
            });
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Thông báo Music For Life";
            String description = "Kênh hiển thị thông báo người theo dõi, tương tác...";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void showSystemNotification(int notifId, String title, String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        notificationManager.notify(notifId, builder.build());
    }

    private void startNotificationPolling() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String myUsername = prefs.getString("userUsername", "");

        if (myUsername.isEmpty()) return;

        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                Call<List<Notification>> call = RetrofitClient.getApiService().getUnreadNotifications(myUsername);
                call.enqueue(new Callback<List<Notification>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Notification>> call, @NonNull Response<List<Notification>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (Notification notif : response.body()) {
                                showSystemNotification(notif.getId(), "Music For Life", notif.getMessage());
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Notification>> call, @NonNull Throwable t) {
                    }
                });
                pollingHandler.postDelayed(this, POLLING_INTERVAL);
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    private void stopNotificationPolling() {
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }
    public void updateUIVisibility(Fragment fragment) {
        if (fragment instanceof DiscoverFragment || fragment instanceof WrappedFragment) {
            if (bottomNavigationView != null) bottomNavigationView.setVisibility(View.GONE);
            if (miniPlayerContainer != null) miniPlayerContainer.setVisibility(View.GONE);
            isHiddenForDiscover = true;
        } else {
            if (bottomNavigationView != null) bottomNavigationView.setVisibility(View.VISIBLE);
            isHiddenForDiscover = false;
            if (MusicService.globalCurrentSong != null && !isMiniPlayerHiddenByUser) {
                if (miniPlayerContainer != null) miniPlayerContainer.setVisibility(View.VISIBLE);
            } else {
                if (miniPlayerContainer != null) miniPlayerContainer.setVisibility(View.GONE);
            }
        }
    }

    public void navigateToDetailFragment(Fragment newFragment, String tag) {
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setReorderingAllowed(true);
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment != null && fragment.isAdded() && !fragment.isHidden()) {
                transaction.hide(fragment);
            }
        }
        updateUIVisibility(newFragment);
        transaction.add(R.id.fragment_container, newFragment, tag)
                .addToBackStack(tag)
                .commit();
    }

    private void setupGestureDetection() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 80;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (isSafeClick()) {
                        Intent intent = new Intent(MainActivity.this, MusicService.class);
                        intent.setAction(diffX > 0 ? MusicService.ACTION_PREV : MusicService.ACTION_NEXT);
                        startService(intent);
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (!isSafeClick()) return true;
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                if (currentSong != null) {
                    intent.putExtra("EXTRA_SONG", currentSong);
                    intent.putExtra("EXTRA_IS_PLAYING", isPlaying);
                }
                ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(MainActivity.this, imgMiniCover, "cover_image_transition");
                startActivity(intent, options.toBundle());
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                isMiniPlayerHiddenByUser = true;
                miniPlayerContainer.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Đã ẩn Mini Player. Chọn lại bài để hiện!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public boolean onDown(MotionEvent e) { return true; }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            if (intent.getBooleanExtra("navigate_to_playlist", false)) {
                if (bottomNavigationView != null) bottomNavigationView.setSelectedItemId(R.id.nav_playlists);
            }
            else if (intent.getBooleanExtra("open_playlist_detail", false)) {
                if (bottomNavigationView != null) bottomNavigationView.setSelectedItemId(R.id.nav_playlists);
                int playlistId = intent.getIntExtra("playlist_id", -1);
                String playlistName = intent.getStringExtra("playlist_name");
                String playlistCover = intent.getStringExtra("playlist_cover");

                if (playlistId != -1) {
                    PlaylistDetailFragment fragment = new PlaylistDetailFragment();
                    Bundle args = new Bundle();
                    args.putInt("EXTRA_PLAYLIST_ID", playlistId);
                    args.putString("EXTRA_PLAYLIST_NAME", playlistName);
                    args.putString("EXTRA_PLAYLIST_COVER", playlistCover);
                    fragment.setArguments(args);
                    navigateToDetailFragment(fragment, "PlaylistDetailFragment");
                }
            }
            else if (intent.getBooleanExtra("open_artist_detail", false)) {
                String artistName = intent.getStringExtra("artist_name");
                if (artistName != null && !artistName.isEmpty()) {
                    ArtistFragment artistFragment = ArtistFragment.newInstance(artistName);
                    navigateToDetailFragment(artistFragment, "ArtistFragment");
                }
            }
        }
    }

    private void renderState(Song newSong, boolean newIsPlaying) {
        if (newSong == null || miniPlayerContainer == null) {
            if (miniPlayerContainer != null) miniPlayerContainer.setVisibility(View.GONE);
            return;
        }

        btnMiniPlay.setImageResource(newIsPlaying ? R.drawable.ic_pause_white : R.drawable.ic_play_white);

        if (this.currentSong == null || this.currentSong.getId() != newSong.getId()) {
            tvMiniTitle.setText(newSong.getTitle());
            tvMiniArtist.setText(newSong.getArtist());
            loadCoverAndPalette(newSong);
            updateMiniHeartUI(newSong.getId());
        }

        if (!isMiniPlayerHiddenByUser && !isHiddenForDiscover) {
            miniPlayerContainer.setVisibility(View.VISIBLE);
        } else {
            miniPlayerContainer.setVisibility(View.GONE);
        }

        this.currentSong = newSong;
        this.isPlaying = newIsPlaying;
    }

    private void updateProgressUI(int currentPosition, int totalDuration) {
        if (progressMiniPlayer != null && totalDuration > 0) {
            if (progressMiniPlayer.getMax() != totalDuration) {
                progressMiniPlayer.setMax(totalDuration);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                progressMiniPlayer.setProgress(currentPosition, true);
            } else {
                progressMiniPlayer.setProgress(currentPosition);
            }
        }
    }

    private void loadCoverAndPalette(Song song) {
        GlideHelper.loadBitmap(this, song.getCoverArtPath(), new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                imgMiniCover.setImageBitmap(resource);

                int songId = song.getId();

                if (miniPlayerColorCache.containsKey(songId) && appBgColorCache.containsKey(songId)) {
                    applyColorsToUI(miniPlayerColorCache.get(songId), appBgColorCache.get(songId));
                    return;
                }

                new Thread(() -> {
                    Palette palette = Palette.from(resource).generate();
                    int baseColor = palette.getVibrantColor(palette.getDominantColor(Color.parseColor("#1A1A1A")));

                    float[] hsvMini = new float[3];
                    Color.colorToHSV(baseColor, hsvMini);
                    if (hsvMini[2] > 0.45f) hsvMini[2] = 0.45f;
                    int miniBgColor = Color.HSVToColor(hsvMini);

                    float[] hsvAppBg = new float[3];
                    Color.colorToHSV(baseColor, hsvAppBg);
                    hsvAppBg[1] = Math.min(1.0f, hsvAppBg[1] * 1.2f);
                    hsvAppBg[2] = 0.25f;
                    int targetAppColor = Color.HSVToColor(hsvAppBg);

                    miniPlayerColorCache.put(songId, miniBgColor);
                    appBgColorCache.put(songId, targetAppColor);

                    new Handler(Looper.getMainLooper()).post(() -> applyColorsToUI(miniBgColor, targetAppColor));
                }).start();
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {}
        });
    }

    private void applyColorsToUI(int miniBgColor, int targetAppColor) {
        Drawable bgDrawable = miniPlayerContainer.getBackground();
        if (bgDrawable != null) {
            bgDrawable = bgDrawable.mutate();
            bgDrawable.setTint(miniBgColor);
            miniPlayerContainer.setBackground(bgDrawable);
        } else {
            miniPlayerContainer.setBackgroundColor(miniBgColor);
        }
        animateAppBackground(targetAppColor);
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

    private void updateMiniHeartUI(int songId) {
        if (btnMiniHeart == null) return;
        boolean isLiked = SongAdapter.globalLikedSongsCache.contains(songId);
        if (isLiked) {
            btnMiniHeart.setImageResource(R.drawable.ic_heart_filled);
            btnMiniHeart.setColorFilter(Color.parseColor("#FF5252"));
        } else {
            btnMiniHeart.setImageResource(R.drawable.ic_heart_outline);
            btnMiniHeart.setColorFilter(Color.parseColor("#E6FFFFFF"));
        }
    }

    private void toggleFavoriteMiniPlayer() {
        if (currentSong == null || btnMiniHeart == null) return;
        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("isLoggedIn", false)) {
            Toast.makeText(this, "Vui lòng đăng nhập để thả tim!", Toast.LENGTH_SHORT).show();
            return;
        }

        String username = prefs.getString("userUsername", "");
        boolean isCurrentlyLiked = SongAdapter.globalLikedSongsCache.contains(currentSong.getId());
        boolean newLikedState = !isCurrentlyLiked;

        if (newLikedState) SongAdapter.globalLikedSongsCache.add(currentSong.getId());
        else SongAdapter.globalLikedSongsCache.remove(currentSong.getId());

        updateMiniHeartUI(currentSong.getId());
        btnMiniHeart.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        Intent favIntent = new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        favIntent.setPackage(getPackageName());
        favIntent.putExtra("EXTRA_SONG", currentSong);
        favIntent.putExtra("EXTRA_IS_LIKED", newLikedState);
        sendBroadcast(favIntent);

        HashMap<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("song_id", currentSong.getId());

        RetrofitClient.getApiService().toggleFavorite(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) revertFavoriteState(isCurrentlyLiked);
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                revertFavoriteState(isCurrentlyLiked);
                Toast.makeText(MainActivity.this, "Lỗi kết nối mạng!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void revertFavoriteState(boolean isCurrentlyLiked) {
        if (isCurrentlyLiked) SongAdapter.globalLikedSongsCache.add(currentSong.getId());
        else SongAdapter.globalLikedSongsCache.remove(currentSong.getId());
        updateMiniHeartUI(currentSong.getId());
        Intent revertIntent = new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        revertIntent.setPackage(getPackageName());
        revertIntent.putExtra("EXTRA_SONG", currentSong);
        revertIntent.putExtra("EXTRA_IS_LIKED", isCurrentlyLiked);
        sendBroadcast(revertIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        startNotificationPolling();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.ACTION_UPDATE_UI);
        filter.addAction(MusicService.ACTION_UPDATE_PROGRESS);
        filter.addAction("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        ContextCompat.registerReceiver(this, musicReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (MusicService.globalCurrentSong != null) {
            renderState(MusicService.globalCurrentSong, MusicService.globalIsPlaying);
        } else {
            if (miniPlayerContainer != null) miniPlayerContainer.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopNotificationPolling();

        try {
            unregisterReceiver(musicReceiver);
        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    public void switchToAccountTab() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_account);
        }
    }
}