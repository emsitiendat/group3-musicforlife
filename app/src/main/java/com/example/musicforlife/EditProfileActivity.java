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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileActivity extends AppCompatActivity {

    private ImageView imgAvatar;
    private EditText edtDisplayName, edtBio;
    private Button btnSave;

    private Uri selectedImageUri = null;
    private String currentUsername;

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

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    Glide.with(this)
                            .load(uri)
                            .circleCrop()
                            .into(imgAvatar);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        mainRootLayout = findViewById(R.id.root_edit_profile);
        currentBgColor = ContextCompat.getColor(this, R.color.bg_main);

        imgAvatar = findViewById(R.id.img_edit_avatar);
        edtDisplayName = findViewById(R.id.edt_edit_display_name);
        edtBio = findViewById(R.id.edt_edit_bio);
        btnSave = findViewById(R.id.btn_save_profile);

        findViewById(R.id.btn_back_edit_profile).setOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        currentUsername = prefs.getString("userUsername", "");
        String currentName = prefs.getString("userName", "");
        String currentAvatar = prefs.getString("userAvatar", "");

        String currentBio = prefs.getString("userBio", "");

        edtDisplayName.setText(currentName);
        edtBio.setText(currentBio);
        GlideHelper.loadCircleForAdapter(this, currentAvatar, imgAvatar);

        imgAvatar.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        btnSave.setOnClickListener(v -> saveProfile());

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

    private void saveProfile() {
        btnSave.setEnabled(false);
        btnSave.setText("Đang lưu...");

        String newName = edtDisplayName.getText().toString().trim();
        String newBio = edtBio.getText().toString().trim();

        RequestBody rbUsername = RequestBody.create(MediaType.parse("text/plain"), currentUsername);
        RequestBody rbDisplayName = RequestBody.create(MediaType.parse("text/plain"), newName);
        RequestBody rbBio = RequestBody.create(MediaType.parse("text/plain"), newBio);

        MultipartBody.Part avatarPart = null;
        if (selectedImageUri != null) {
            File imageFile = getFileFromUri(selectedImageUri);
            if (imageFile != null) {
                RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), imageFile);
                avatarPart = MultipartBody.Part.createFormData("avatar", imageFile.getName(), requestFile);
            }
        }

        RetrofitClient.getApiService().updateProfile(rbUsername, rbDisplayName, rbBio, avatarPart)
                .enqueue(new Callback<ProfileUpdateResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ProfileUpdateResponse> call, @NonNull Response<ProfileUpdateResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();

                            editor.putString("userName", newName);
                            editor.putString("userBio", newBio);

                            if (response.body().getAvatarUrl() != null) {
                                editor.putString("userAvatar", response.body().getAvatarUrl());
                            }
                            editor.apply();

                            Toast.makeText(EditProfileActivity.this, "Đã cập nhật hồ sơ!", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            btnSave.setEnabled(true);
                            btnSave.setText("Lưu thay đổi");
                            Toast.makeText(EditProfileActivity.this, "Lỗi server!", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ProfileUpdateResponse> call, @NonNull Throwable t) {
                        btnSave.setEnabled(true);
                        btnSave.setText("Lưu thay đổi");
                        Toast.makeText(EditProfileActivity.this, "Lỗi mạng!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private File getFileFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            File tempFile = new File(getCacheDir(), "temp_avatar.jpg");
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            return tempFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            android.view.View v = getCurrentFocus();
            if (v instanceof android.widget.EditText) {
                android.graphics.Rect outRect = new android.graphics.Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    v.clearFocus();
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}