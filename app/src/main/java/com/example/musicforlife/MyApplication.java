package com.example.musicforlife;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

public class MyApplication extends Application {

    public static final String CHANNEL_ID = "channel_music_for_life";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        applySavedTheme();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Kênh phát nhạc",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void applySavedTheme() {
        SharedPreferences prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", true);

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
}