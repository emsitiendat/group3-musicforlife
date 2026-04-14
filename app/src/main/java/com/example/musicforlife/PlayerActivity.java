package com.example.musicforlife;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlayerActivity extends AppCompatActivity implements BottomSheetMenuFragment.BottomSheetListener {

    private ImageView imgCover, imgPlayerBgBlur;
    private View playerScrim;
    private TextView tvTitle, tvArtist, tvCurrentTime, tvTotalTime, tvListeningNow;
    private ImageButton btnPlayPause, btnBack, btnNext, btnPrev, btnShuffle, btnRepeat, btnHeart;
    private SeekBar seekBar;
    private ImageButton btnMore, btnLyrics, btnComments, btnShare;

    private float currentSpeed = 1.0f;
    private static final String FILE_PROVIDER_AUTHORITY = "com.example.musicforlife.fileprovider";

    private TextView tvZenQuote;
    private String[] zenQuotes = {
            "Tập trung là chìa khóa của mọi thành công.",
            "Đừng để mạng xã hội đánh cắp ước mơ của bạn.",
            "Một giờ tập trung bằng ba giờ xao nhãng.",
            "Âm nhạc tĩnh tâm, trí tuệ khai sáng.",
            "Hãy làm việc trong im lặng, để thành công tự lên tiếng.",
            "Kỷ luật là cầu nối giữa mục tiêu và thành tựu."
    };

    private ConstraintLayout zenModeLayout;
    private TextView tvZenTimer;
    private Button btnStopZen;
    private boolean isZenMode = false;

    private boolean isLiked = false;
    private Song currentSong;
    private int lastSongId = -1;
    private boolean isUserSeeking = false;

    private View currentCustomToast = null;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;

    private final BroadcastReceiver playerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (MusicService.ACTION_UPDATE_UI.equals(action)) {
                Song song = (Song) intent.getSerializableExtra("EXTRA_SONG");

                boolean isServiceZenMode = intent.getBooleanExtra("EXTRA_IS_ZEN_MODE", false);
                if (isServiceZenMode != isZenMode) {
                    isZenMode = isServiceZenMode;
                    if (isZenMode) showZenModeUI();
                    else hideZenModeUI();
                }

                boolean isShuffle = intent.getBooleanExtra("EXTRA_IS_SHUFFLE", false);
                boolean isRepeat = intent.getBooleanExtra("EXTRA_IS_REPEAT", false);
                updateShuffleRepeatUI(isShuffle, isRepeat);

                if (song != null) {
                    MusicService.globalCurrentSong = song;
                    updateUI();
                }
                updatePlayPauseButtonUI();

            } else if (MusicService.ACTION_UPDATE_PROGRESS.equals(action)) {
                int currentPosition = intent.getIntExtra("EXTRA_CURRENT_POSITION", 0);
                int totalDuration = intent.getIntExtra("EXTRA_TOTAL_DURATION", 0);

                if (!isUserSeeking) {
                    if (seekBar.getMax() != totalDuration) {
                        seekBar.setMax(totalDuration);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        seekBar.setProgress(currentPosition, true);
                    } else {
                        seekBar.setProgress(currentPosition);
                    }
                    tvCurrentTime.setText(formatTime(currentPosition));
                    tvTotalTime.setText(formatTime(totalDuration));
                }

            } else if ("com.example.musicforlife.ACTION_FAVORITE_CHANGED".equals(action)) {
                if (currentSong != null) {
                    isLiked = SongAdapter.globalLikedSongsCache.contains(currentSong.getId());
                    updateFavoriteUI();
                }

            } else if (MusicService.ACTION_UPDATE_ZEN_TIMER.equals(action)) {
                long millisUntilFinished = intent.getLongExtra("EXTRA_ZEN_MILLIS", 0);
                int minutes = (int) (millisUntilFinished / (1000 * 60));
                int seconds = (int) (millisUntilFinished / 1000) % 60;
                if (tvZenTimer != null) tvZenTimer.setText(String.format("%02d:%02d", minutes, seconds));

            } else if (MusicService.ACTION_ZEN_MODE_FINISHED.equals(action)) {
                if (tvZenTimer != null) tvZenTimer.setText("00:00");

                showPremiumTopToast("Hoàn thành thời gian tập trung!", R.drawable.ic_zen_mode);

                hideZenModeUI();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        imgPlayerBgBlur = findViewById(R.id.img_player_bg_blur);
        playerScrim = findViewById(R.id.player_color_scrim);
        imgCover = findViewById(R.id.img_player_cover);
        tvTitle = findViewById(R.id.tv_player_title);
        tvArtist = findViewById(R.id.tv_player_artist);
        btnPlayPause = findViewById(R.id.btn_player_play_pause);
        btnBack = findViewById(R.id.btn_collapse_player);
        btnNext = findViewById(R.id.btn_player_next);
        btnPrev = findViewById(R.id.btn_player_prev);
        btnShuffle = findViewById(R.id.btn_player_shuffle);
        btnRepeat = findViewById(R.id.btn_player_repeat);
        btnHeart = findViewById(R.id.btn_player_heart);
        seekBar = findViewById(R.id.seek_bar_player);
        tvCurrentTime = findViewById(R.id.tv_player_current_time);
        tvTotalTime = findViewById(R.id.tv_player_total_time);

        tvListeningNow = findViewById(R.id.tv_player_listening_now);
        btnMore = findViewById(R.id.btn_player_more);
        btnLyrics = findViewById(R.id.btn_player_lyrics);
        btnComments = findViewById(R.id.btn_player_comments);
        btnShare = findViewById(R.id.btn_player_share);

        zenModeLayout = findViewById(R.id.layout_zen_mode);
        tvZenTimer = findViewById(R.id.tv_zen_timer);
        btnStopZen = findViewById(R.id.btn_stop_zen);
        tvZenQuote = findViewById(R.id.tv_zen_quote);

        tvListeningNow.setText("Đang kết nối...");
        tvArtist.setOnClickListener(v -> navigateToArtist());

        btnMore.setOnClickListener(v -> {
            BottomSheetMenuFragment bottomSheet = BottomSheetMenuFragment.newInstance(currentSpeed);
            bottomSheet.show(getSupportFragmentManager(), "BottomSheetMenu");
        });

        btnComments.setOnClickListener(v -> {
            if (currentSong != null) {
                SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                String loggedInUser = prefs.getString("userUsername", "");
                if (loggedInUser.isEmpty()) {
                    showPremiumTopToast("Vui lòng đăng nhập để bình luận", 0);
                    return;
                }
                CommentBottomSheetFragment sheet = CommentBottomSheetFragment.newInstance(currentSong.getId(), loggedInUser);
                sheet.show(getSupportFragmentManager(), "CommentSheet");
            }
        });

        btnShare.setOnClickListener(v -> showCustomShareSheet());

        btnLyrics.setOnClickListener(v -> fetchAndShowLyrics());

        updateShuffleRepeatUI(MusicService.globalIsShuffle, MusicService.globalIsRepeat);

        btnPlayPause.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(PlayerActivity.this, MusicService.class);
            serviceIntent.setAction(MusicService.globalIsPlaying ? MusicService.ACTION_PAUSE : MusicService.ACTION_RESUME);
            startService(serviceIntent);
        });

        btnNext.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(PlayerActivity.this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_NEXT);
            startService(serviceIntent);
        });

        btnPrev.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(PlayerActivity.this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_PREV);
            startService(serviceIntent);
        });

        btnShuffle.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(PlayerActivity.this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_TOGGLE_SHUFFLE);
            startService(serviceIntent);
        });

        btnRepeat.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(PlayerActivity.this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_TOGGLE_REPEAT);
            startService(serviceIntent);
        });

        btnHeart.setOnClickListener(v -> toggleFavorite());
        btnBack.setOnClickListener(v -> finish());

        if (btnStopZen != null) btnStopZen.setOnClickListener(v -> showExitZenDialog());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) tvCurrentTime.setText(formatTime(progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                Intent seekIntent = new Intent(PlayerActivity.this, MusicService.class);
                seekIntent.setAction(MusicService.ACTION_SEEK);
                seekIntent.putExtra("EXTRA_SEEK_POSITION", seekBar.getProgress());
                startService(seekIntent);
            }
        });

        if (imgCover != null) {
            imgCover.setOnTouchListener(new OnSwipeTouchListener(PlayerActivity.this) {
                @Override public void onSwipeRight() { btnPrev.performClick(); }
                @Override public void onSwipeLeft() { btnNext.performClick(); }
                @Override public void onDoubleTapAction() { toggleFavorite(); }
            });
        }

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mShakeDetector = new ShakeDetector();
            mShakeDetector.setOnShakeListener(() -> {
                btnNext.performClick();
                showPremiumTopToast("Đổi bài!", 0);
            });
        }

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isZenMode) {
                    showExitZenDialog();
                } else {
                    this.setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void showCustomShareSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_share, null);

        if (view == null) {
            shareSystemDefault();
            return;
        }
        dialog.setContentView(view);

        view.findViewById(R.id.btn_share_story_ig).setOnClickListener(v -> {
            dialog.dismiss();
            shareToStory();
        });

        view.findViewById(R.id.btn_share_copy_link).setOnClickListener(v -> {
            dialog.dismiss();
            copySongLinkToClipboard();
        });

        view.findViewById(R.id.btn_share_more).setOnClickListener(v -> {
            dialog.dismiss();
            shareSystemDefault();
        });

        View btnShareCommunity = view.findViewById(R.id.btn_share_community);
        if (btnShareCommunity != null) {
            btnShareCommunity.setOnClickListener(v -> {
                dialog.dismiss();

                if (currentSong != null) {
                    Intent intent = new Intent(PlayerActivity.this, CreatePostActivity.class);
                    intent.putExtra("SONG_ID", currentSong.getId());
                    intent.putExtra("SONG_TITLE", currentSong.getTitle());
                    startActivity(intent);
                } else {
                    showPremiumTopToast("Chưa tải xong bài hát, vui lòng đợi!", 0);
                }
            });
        }

        dialog.show();
    }
    private void copySongLinkToClipboard() {
        if (currentSong == null) return;
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(
                "Music Link",
                "Cùng nghe bài hát " + currentSong.getTitle() + " của " + currentSong.getArtist() + " trên Music For Life nhé!"
        );
        clipboard.setPrimaryClip(clip);
        showPremiumTopToast("Đã sao chép liên kết!", 0);
    }

    private void shareSystemDefault() {
        if (currentSong == null) return;
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "Đang nghe " + currentSong.getTitle() + " - " + currentSong.getArtist() + " trên Music For Life. Cùng nghe nhé!");
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Chia sẻ qua..."));
    }

    private void showExitZenDialog() {
        BottomSheetDialog exitDialog = new BottomSheetDialog(PlayerActivity.this);
        View exitView = getLayoutInflater().inflate(R.layout.bottom_sheet_exit_zen, null);

        if (exitView == null) {
            LinearLayout layout = new LinearLayout(PlayerActivity.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(60, 60, 60, 60);
            layout.setBackgroundColor(Color.parseColor("#19152B"));

            TextView title = new TextView(PlayerActivity.this);
            title.setText("Thoát chế độ Zen?");
            title.setTextSize(20f);
            title.setTextColor(Color.WHITE);
            title.setTypeface(null, Typeface.BOLD);

            TextView desc = new TextView(PlayerActivity.this);
            desc.setText("\nĐồng hồ tập trung sẽ dừng lại. Bạn chắc chứ?\n");
            desc.setTextColor(Color.LTGRAY);

            Button btnExit = new Button(PlayerActivity.this);
            btnExit.setText("Thoát");
            btnExit.setTextColor(Color.parseColor("#FF5252"));
            btnExit.setBackgroundColor(Color.TRANSPARENT);
            btnExit.setOnClickListener(v -> {
                exitDialog.dismiss();
                stopZenMode();
            });

            layout.addView(title); layout.addView(desc); layout.addView(btnExit);
            exitDialog.setContentView(layout);
        } else {
            exitDialog.setContentView(exitView);
            exitView.findViewById(R.id.btn_confirm_exit_zen).setOnClickListener(v -> {
                exitDialog.dismiss();
                stopZenMode();
            });
        }
        exitDialog.show();
    }

    private void showPremiumTopToast(String message, int iconResId) {
        ConstraintLayout root = findViewById(R.id.player_main_layout);
        if (root == null) return;

        if (currentCustomToast != null) {
            root.removeView(currentCustomToast);
            currentCustomToast.animate().cancel();
        }

        LinearLayout toastLayout = new LinearLayout(this);
        toastLayout.setOrientation(LinearLayout.HORIZONTAL);
        toastLayout.setBackgroundResource(R.drawable.bg_rounded_button);
        toastLayout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#251E3E")));
        toastLayout.setPadding(48, 24, 48, 24);
        toastLayout.setGravity(android.view.Gravity.CENTER);
        toastLayout.setElevation(50f);

        if (iconResId != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconResId);
            icon.setColorFilter(Color.parseColor("#00C44A"));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    (int) (20 * getResources().getDisplayMetrics().density),
                    (int) (20 * getResources().getDisplayMetrics().density)
            );
            iconParams.setMargins(0, 0, (int) (12 * getResources().getDisplayMetrics().density), 0);
            toastLayout.addView(icon, iconParams);
        }

        TextView textView = new TextView(this);
        textView.setText(message);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(14f);
        textView.setTypeface(null, Typeface.BOLD);
        toastLayout.addView(textView);

        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        params.setMargins(0, (int) (50 * getResources().getDisplayMetrics().density), 0, 0);
        toastLayout.setLayoutParams(params);

        root.addView(toastLayout);
        currentCustomToast = toastLayout;

        toastLayout.setTranslationY(-150f);
        toastLayout.setAlpha(0f);
        toastLayout.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .withEndAction(() -> {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (currentCustomToast == toastLayout) {
                            toastLayout.animate()
                                    .translationY(-150f)
                                    .alpha(0f)
                                    .setDuration(300)
                                    .setInterpolator(new AccelerateInterpolator())
                                    .withEndAction(() -> {
                                        root.removeView(toastLayout);
                                        if (currentCustomToast == toastLayout) currentCustomToast = null;
                                    }).start();
                        }
                    }, 2500);
                }).start();
    }

    private void updateUI() {
        Song song = MusicService.globalCurrentSong;
        if (song == null) return;

        currentSong = song;

        if (seekBar != null) {
            if (seekBar.getMax() != MusicService.globalTotalDuration) {
                seekBar.setMax(MusicService.globalTotalDuration);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                seekBar.setProgress(MusicService.globalCurrentPosition, true);
            } else {
                seekBar.setProgress(MusicService.globalCurrentPosition);
            }
        }
        if (tvCurrentTime != null) tvCurrentTime.setText(formatTime(MusicService.globalCurrentPosition));
        if (tvTotalTime != null) tvTotalTime.setText(formatTime(MusicService.globalTotalDuration));

        if (lastSongId != song.getId()) {
            tvTitle.setText(song.getTitle());
            tvArtist.setText(song.getArtist());

            isLiked = SongAdapter.globalLikedSongsCache.contains(song.getId());
            updateFavoriteUI();

            Glide.with(this)
                    .asBitmap()
                    .load(Utils.normalizeUrl(song.getCoverArtPath()))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {


                            if (imgPlayerBgBlur != null) {
                                imgPlayerBgBlur.setImageBitmap(bitmap);
                            }

                            Glide.with(PlayerActivity.this)
                                    .load(bitmap)
                                    .transform(new RoundedCorners(64))
                                    .dontAnimate()
                                    .into(imgCover);

                            Palette.from(bitmap).generate(palette -> {
                                if (palette != null) {
                                }
                            });
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            if (imgCover != null) {
                                imgCover.setImageDrawable(placeholder);
                            }
                        }
                    });

            lastSongId = song.getId();
        }
    }
    private void updatePlayPauseButtonUI() {
        if (btnPlayPause == null) return;
        boolean isNowPlaying = MusicService.globalIsPlaying;

        if (isNowPlaying) {
            btnPlayPause.setImageResource(R.drawable.ic_pause_white);
            btnPlayPause.setColorFilter(Color.BLACK);
            if (imgCover != null) imgCover.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).start();
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play_white);
            btnPlayPause.setColorFilter(Color.BLACK);
            if (imgCover != null) imgCover.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.ACTION_UPDATE_UI);
        filter.addAction(MusicService.ACTION_UPDATE_PROGRESS);
        filter.addAction("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        filter.addAction(MusicService.ACTION_UPDATE_ZEN_TIMER);
        filter.addAction(MusicService.ACTION_ZEN_MODE_FINISHED);
        ContextCompat.registerReceiver(this, playerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (mSensorManager != null && mAccelerometer != null) {
            mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        }

        updateUI();
        updatePlayPauseButtonUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(playerReceiver);
        if (mSensorManager != null) mSensorManager.unregisterListener(mShakeDetector);
    }

    @Override
    public void onOptionClick(int optionId) {
        switch (optionId) {
            case 1: showPlaylistOptions(); break;
            case 3: showTimerOptions(); break;
            case 4: startZenMode(); break;
            case 5: navigateToArtist(); break;
            case 6: showCustomShareSheet(); break;
        }
    }

    @Override
    public void onSpeedChanged(float speed) {
        currentSpeed = speed;
        sendExoActionToService(MusicService.ACTION_CHANGE_SPEED, currentSpeed);
    }

    private void navigateToArtist() {
        String targetArtistName = "";
        if (currentSong != null && currentSong.getArtist() != null) {
            targetArtistName = currentSong.getArtist();
        } else if (tvArtist.getText() != null && !tvArtist.getText().toString().equals("...") && !tvArtist.getText().toString().isEmpty()) {
            targetArtistName = tvArtist.getText().toString();
        }

        if (!targetArtistName.isEmpty()) {
            Intent intent = new Intent(PlayerActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("open_artist_detail", true);
            intent.putExtra("artist_name", targetArtistName);
            startActivity(intent);
            finish();
        }
    }

    private void shareToStory() {
        if (currentSong == null || imgCover.getDrawable() == null) {
            showPremiumTopToast("Đang tải ảnh, vui lòng thử lại sau!", 0);
            return;
        }

        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");
        if (username.isEmpty()) {
            showPremiumTopToast("Vui lòng đăng nhập để đăng Story!", 0);
            return;
        }

        final EditText input = new EditText(this);
        input.setHint("Nhập cảm nghĩ của bạn...");
        input.setPadding(40, 40, 40, 40);

        new AlertDialog.Builder(this)
                .setTitle("Đăng Story")
                .setMessage("Cảm nghĩ của bạn về bài hát này là gì?")
                .setView(input)
                .setPositiveButton("Đăng lên Story", (dialog, which) -> {
                    String caption = input.getText().toString().trim();
                    generateAndUploadStory(username, caption);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void generateAndUploadStory(String username, String caption) {
        try {
            int size = 1080;
            Bitmap bitmap = Bitmap.createBitmap(size, size + 500, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            canvas.drawColor(Color.parseColor("#19152B"));

            Drawable originalDrawable = imgCover.getDrawable();
            Drawable coverCopy = originalDrawable.getConstantState().newDrawable().mutate();
            int padding = 80;
            coverCopy.setBounds(padding, padding, size - padding, size - padding);
            coverCopy.draw(canvas);

            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(64);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(currentSong.getTitle(), size / 2f, size, textPaint);

            textPaint.setTextSize(48);
            textPaint.setColor(Color.parseColor("#9CA3AF"));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            canvas.drawText(currentSong.getArtist(), size / 2f, size + 80, textPaint);

            if (!caption.isEmpty()) {
                textPaint.setTextSize(55);
                textPaint.setColor(Color.parseColor("#00C44A"));
                textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
                canvas.drawText("\"" + caption + "\"", size / 2f, size + 180, textPaint);
            }

            textPaint.setTextSize(32);
            textPaint.setColor(Color.parseColor("#FF5252"));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("MUSIC FOR LIFE STORY", size / 2f, size + 350, textPaint);

            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs();
            File imageFile = new File(cachePath, "story_upload.png");
            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            uploadStoryFile(imageFile, username);

        } catch (Exception e) {
            showPremiumTopToast("Lỗi khi tạo ảnh Story!", 0);
        }
    }

    private void uploadStoryFile(File file, String username) {
        showPremiumTopToast("Đang đăng Story, chờ chút nhé...", 0);

        okhttp3.RequestBody requestFile = okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/png"), file);
        okhttp3.MultipartBody.Part body = okhttp3.MultipartBody.Part.createFormData("image", file.getName(), requestFile);
        okhttp3.RequestBody userBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), username);

        RetrofitClient.getApiService().uploadStory(userBody, body).enqueue(new retrofit2.Callback<SimpleResponse>() {
            @Override
            public void onResponse(retrofit2.Call<SimpleResponse> call, retrofit2.Response<SimpleResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    showPremiumTopToast("Đã đăng Story thành công! (Tồn tại 24h)", R.drawable.ic_zen_mode);
                } else {
                    showPremiumTopToast("Lỗi khi tải Story lên server!", 0);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<SimpleResponse> call, Throwable t) {
                showPremiumTopToast("Lỗi kết nối mạng!", 0);
            }
        });
    }
    private void showTimerOptions() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_timer, null);

        if (view == null) return;
        dialog.setContentView(view);

        View.OnClickListener listener = v -> {
            int minutes = 0;
            if (v.getId() == R.id.btn_timer_track_end) minutes = -1;
            else if (v.getId() == R.id.btn_timer_15) minutes = 15;
            else if (v.getId() == R.id.btn_timer_30) minutes = 30;
            else if (v.getId() == R.id.btn_timer_45) minutes = 45;
            else if (v.getId() == R.id.btn_timer_60) minutes = 60;

            Intent serviceIntent = new Intent(PlayerActivity.this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_SET_SLEEP_TIMER);
            serviceIntent.putExtra("EXTRA_SLEEP_MINUTES", minutes);
            startService(serviceIntent);

            if (minutes == -1) showPremiumTopToast("Nhạc sẽ dừng sau bài này", 0);
            else if (minutes > 0) showPremiumTopToast("Sẽ tắt nhạc sau " + minutes + " phút", 0);
            else showPremiumTopToast("Đã tắt hẹn giờ", 0);

            dialog.dismiss();
        };

        view.findViewById(R.id.btn_timer_track_end).setOnClickListener(listener);
        view.findViewById(R.id.btn_timer_15).setOnClickListener(listener);
        view.findViewById(R.id.btn_timer_30).setOnClickListener(listener);
        view.findViewById(R.id.btn_timer_45).setOnClickListener(listener);
        view.findViewById(R.id.btn_timer_60).setOnClickListener(listener);
        view.findViewById(R.id.btn_timer_off).setOnClickListener(listener);

        dialog.show();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isZenMode) stopZenMode();
    }

    private void sendExoActionToService(String action, float value) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        intent.putExtra("EXTRA_EXO_VALUE", value);
        startService(intent);
    }

    private void fetchAndShowLyrics() {
        if (currentSong == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_lyrics, null);
        if (view == null) return;
        dialog.setContentView(view);

        View internal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (internal != null) {
            internal.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
        }

        TextView tvTitleSheet = view.findViewById(R.id.tv_lyrics_title);
        TextView tvArtistSheet = view.findViewById(R.id.tv_lyrics_artist);
        TextView tvContent = view.findViewById(R.id.tv_lyrics_content);

        tvTitleSheet.setText(currentSong.getTitle());
        tvArtistSheet.setText("Ca sĩ: " + currentSong.getArtist());
        dialog.show();

        RetrofitClient.getApiService().getSongLyrics(currentSong.getId()).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tvContent.setText(response.body().get("lyrics").getAsString());
                } else {
                    tvContent.setText("Chưa có lời bài hát cho bài này.\nBạn hãy quay lại sau nhé!");
                    tvContent.setTextColor(Color.parseColor("#9CA3AF"));
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                tvContent.setText("Lỗi kết nối mạng, không thể tải lời bài hát.");
            }
        });
    }

    private void startZenMode() {
        if (!MusicService.globalIsPlaying) {
            showPremiumTopToast("Vui lòng phát nhạc trước khi bật Chế độ Zen!", 0);
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_zen, null);
        if (view == null) return;
        dialog.setContentView(view);

        View.OnClickListener listener = v -> {
            int minutes = 25;
            if (v.getId() == R.id.btn_zen_15) minutes = 15;
            else if (v.getId() == R.id.btn_zen_50) minutes = 50;
            else if (v.getId() == R.id.btn_zen_90) minutes = 90;

            Intent serviceIntent = new Intent(this, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_START_ZEN_MODE);
            serviceIntent.putExtra("EXTRA_ZEN_MINUTES", minutes);
            startService(serviceIntent);

            showPremiumTopToast("Đã bật Chế độ Zen " + minutes + " phút!", R.drawable.ic_zen_mode);
            dialog.dismiss();
        };

        view.findViewById(R.id.btn_zen_15).setOnClickListener(listener);
        view.findViewById(R.id.btn_zen_25).setOnClickListener(listener);
        view.findViewById(R.id.btn_zen_50).setOnClickListener(listener);
        view.findViewById(R.id.btn_zen_90).setOnClickListener(listener);
        dialog.show();
    }

    private void stopZenMode() {
        Intent serviceIntent = new Intent(this, MusicService.class);
        serviceIntent.setAction(MusicService.ACTION_STOP_ZEN_MODE);
        startService(serviceIntent);
    }

    private void showZenModeUI() {
        if (tvZenQuote == null) tvZenQuote = findViewById(R.id.tv_zen_quote);
        if (tvZenQuote != null) {
            int randomIndex = (int) (Math.random() * zenQuotes.length);
            tvZenQuote.setText("\"" + zenQuotes[randomIndex] + "\"");
        }

        if (zenModeLayout != null) {
            zenModeLayout.setVisibility(View.VISIBLE);
            zenModeLayout.bringToFront();
            zenModeLayout.setElevation(100f);
            zenModeLayout.setClickable(true);
            zenModeLayout.setFocusable(true);
        }
        if (btnBack != null) {
            btnBack.setColorFilter(Color.WHITE);
            btnBack.setAlpha(0.3f);
        }
    }

    private void hideZenModeUI() {
        isZenMode = false;
        if (zenModeLayout != null) zenModeLayout.setVisibility(View.GONE);
        if (btnBack != null) {
            btnBack.setColorFilter(Color.WHITE);
            btnBack.setAlpha(1.0f);
        }
    }

    private void updateShuffleRepeatUI(boolean isShuffle, boolean isRepeat) {
        if (btnShuffle != null) {
            btnShuffle.setColorFilter(isShuffle ? Color.parseColor("#1DB954") : Color.WHITE);
            btnShuffle.setAlpha(isShuffle ? 1.0f : 0.6f);
        }
        if (btnRepeat != null) {
            btnRepeat.setColorFilter(isRepeat ? Color.parseColor("#1DB954") : Color.WHITE);
            btnRepeat.setAlpha(isRepeat ? 1.0f : 0.6f);
        }
    }

    private void updateFavoriteUI() {
        if (btnHeart != null) {
            if (isLiked) {
                btnHeart.setImageResource(R.drawable.ic_heart_filled);
                btnHeart.setColorFilter(Color.parseColor("#FF5252"));
            } else {
                btnHeart.setImageResource(R.drawable.ic_heart_outline);
                btnHeart.setColorFilter(Color.WHITE);
            }
        }
    }

    private void toggleFavorite() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("isLoggedIn", false) || currentSong == null) {
            showPremiumTopToast("Vui lòng đăng nhập để thả tim!", 0);
            return;
        }

        isLiked = !isLiked;

        if (isLiked) SongAdapter.globalLikedSongsCache.add(currentSong.getId());
        else SongAdapter.globalLikedSongsCache.remove(currentSong.getId());

        updateFavoriteUI();

        if (btnHeart != null) {
            btnHeart.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            btnHeart.animate().scaleX(1.4f).scaleY(1.4f).setDuration(150)
                    .withEndAction(() -> btnHeart.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()).start();
        }

        Intent favIntent = new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        favIntent.setPackage(getPackageName());
        favIntent.putExtra("EXTRA_SONG", currentSong);
        favIntent.putExtra("EXTRA_IS_LIKED", isLiked);
        sendBroadcast(favIntent);

        HashMap<String, Object> body = new HashMap<>();
        body.put("username", prefs.getString("userUsername", ""));
        body.put("song_id", currentSong.getId());

        RetrofitClient.getApiService().toggleFavorite(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    revertHeartState();
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                revertHeartState();
            }
        });
    }

    private void revertHeartState() {
        isLiked = !isLiked;
        if (isLiked) SongAdapter.globalLikedSongsCache.add(currentSong.getId());
        else SongAdapter.globalLikedSongsCache.remove(currentSong.getId());
        updateFavoriteUI();

        Intent favIntent = new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        favIntent.setPackage(getPackageName());
        favIntent.putExtra("EXTRA_SONG", currentSong);
        favIntent.putExtra("EXTRA_IS_LIKED", isLiked);
        sendBroadcast(favIntent);
    }

    private void showPlaylistOptions() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("isLoggedIn", false) || currentSong == null) {
            showPremiumTopToast("Vui lòng đăng nhập để tạo Playlist!", 0);
            return;
        }
        RetrofitClient.getApiService().getUserPlaylists(prefs.getString("userUsername", "")).enqueue(new Callback<List<Playlist>>() {            public void onResponse(@NonNull Call<List<Playlist>> call, @NonNull Response<List<Playlist>> response) {
                if (response.isSuccessful() && response.body() != null)
                    showDialogSelectPlaylist(response.body(), prefs.getString("userUsername", ""));
            }
            @Override
            public void onFailure(@NonNull Call<List<Playlist>> call, @NonNull Throwable t) {}
        });
    }

    private void showDialogSelectPlaylist(List<Playlist> playlists, String username) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_playlists, null);
        if (view == null) return;
        dialog.setContentView(view);

        view.findViewById(R.id.btn_create_playlist).setOnClickListener(v -> {
            dialog.dismiss();
            showDialogCreateNewPlaylist(username);
        });

        LinearLayout container = view.findViewById(R.id.layout_playlist_container);

        for (Playlist playlist : playlists) {
            TextView btnPlaylist = new TextView(this);
            btnPlaylist.setText(playlist.getName());
            btnPlaylist.setTextSize(16f);
            btnPlaylist.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            btnPlaylist.setPadding(60, 40, 60, 40);

            int[] attrs = new int[]{android.R.attr.selectableItemBackground};
            android.content.res.TypedArray typedArray = obtainStyledAttributes(attrs);
            int backgroundResource = typedArray.getResourceId(0, 0);
            btnPlaylist.setBackgroundResource(backgroundResource);
            typedArray.recycle();

            btnPlaylist.setOnClickListener(v -> {
                addSongToPlaylist(playlist);
                dialog.dismiss();
            });
            container.addView(btnPlaylist);
        }
        dialog.show();
    }

    private void showDialogCreateNewPlaylist(String username) {
        BottomSheetDialog createDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_create_playlist, null);

        if (view == null) return;
        createDialog.setContentView(view);

        EditText etName = view.findViewById(R.id.et_playlist_name);
        EditText etDesc = view.findViewById(R.id.et_playlist_description);
        com.google.android.material.switchmaterial.SwitchMaterial switchPublic = view.findViewById(R.id.switch_public);
        TextView btnCancel = view.findViewById(R.id.btn_cancel_create);
        TextView btnConfirm = view.findViewById(R.id.btn_confirm_create);

        btnCancel.setOnClickListener(v -> createDialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();
            boolean isPublic = switchPublic.isChecked();

            if (name.isEmpty()) {
                showPremiumTopToast("Tên Playlist không được để trống!", 0);
                return;
            }

            okhttp3.RequestBody usernameBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), username);
            okhttp3.RequestBody nameBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), name);
            okhttp3.RequestBody descBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), desc);
            okhttp3.RequestBody isPublicBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), String.valueOf(isPublic));

            showPremiumTopToast("Đang tạo Playlist...", 0);

            RetrofitClient.getApiService().createPlaylist(usernameBody, nameBody, descBody, isPublicBody, null)
                    .enqueue(new Callback<JsonObject>() {
                        @Override
                        public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                            if (response.isSuccessful()) {
                                showPremiumTopToast("Đã tạo Playlist thành công!", R.drawable.ic_zen_mode);
                                createDialog.dismiss();
                            } else {
                                showPremiumTopToast("Lỗi Server khi tạo Playlist!", 0);
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                            showPremiumTopToast("Lỗi mạng! Không thể tạo Playlist.", 0);
                        }
                    });
        });

        androidx.cardview.widget.CardView tagChill = view.findViewById(R.id.tag_chill);
        androidx.cardview.widget.CardView tagFocus = view.findViewById(R.id.tag_focus);
        androidx.cardview.widget.CardView tagSad = view.findViewById(R.id.tag_sad);

        if (tagChill != null) tagChill.setOnClickListener(v -> etName.setText("🎧 Cực Chill"));
        if (tagFocus != null) tagFocus.setOnClickListener(v -> etName.setText("💻 Code tới sáng"));
        if (tagSad != null) tagSad.setOnClickListener(v -> etName.setText("🌧️ Suy"));

        createDialog.show();
    }
    private void addSongToPlaylist(Playlist playlist) {
        int playlistId = playlist.getId();
        HashMap<String, Object> body = new HashMap<>();
        body.put("playlist_id", playlistId);
        body.put("song_id", currentSong.getId());

        RetrofitClient.getApiService().addSongToPlaylist(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showPremiumTopToast(response.body().get("message").getAsString(), 0);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {}
        });
    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}