package com.example.musicforlife;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.gson.JsonObject;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MusicService extends Service {

    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_RESUME = "ACTION_RESUME";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_PREV = "ACTION_PREV";
    public static final String ACTION_SEEK = "ACTION_SEEK";
    public static final String ACTION_UPDATE_UI = "ACTION_UPDATE_UI";
    public static final String ACTION_UPDATE_PROGRESS = "ACTION_UPDATE_PROGRESS";
    public static final String ACTION_TOGGLE_SHUFFLE = "ACTION_TOGGLE_SHUFFLE";
    public static final String ACTION_TOGGLE_REPEAT = "ACTION_TOGGLE_REPEAT";
    public static final String ACTION_SET_SLEEP_TIMER = "ACTION_SET_SLEEP_TIMER";
    public static final String ACTION_CHANGE_SPEED = "ACTION_CHANGE_SPEED";
    public static final String ACTION_START_ZEN_MODE = "ACTION_START_ZEN_MODE";
    public static final String ACTION_STOP_ZEN_MODE = "ACTION_STOP_ZEN_MODE";
    public static final String ACTION_UPDATE_ZEN_TIMER = "ACTION_UPDATE_ZEN_TIMER";
    public static final String ACTION_ZEN_MODE_FINISHED = "ACTION_ZEN_MODE_FINISHED";

    public static Song globalCurrentSong = null;
    public static boolean globalIsPlaying = false;
    public static boolean globalIsShuffle = false;
    public static boolean globalIsRepeat = false;
    public static boolean globalIsZenMode = false;
    public static int globalCurrentPosition = 0;
    public static int globalTotalDuration = 0;

    private ExoPlayer exoPlayer;
    private float currentSpeed = 1.0f;
    private Song currentSong;
    private List<Song> currentSongList = new ArrayList<>();
    private int currentPositionInList = -1;

    private int startPosition = 0;

    private boolean isHistoryRecorded = false;
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    private boolean stopAfterCurrentTrack = false;

    private boolean isFetchingMore = false;
    private boolean hasPreloadedForCurrentSong = false;
    private boolean isForeground = false;
    private boolean isSwitchingSong = false;
    private long lastPreloadTime = 0;

    private Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable sleepTimerRunnable;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Handler fadeHandler = new Handler(Looper.getMainLooper());

    private CountDownTimer zenTimer;
    private MediaSessionCompat mediaSession;

    private CustomTarget<Bitmap> notificationTarget;
    private Bitmap cachedNotificationBitmap = null;
    private int cachedSongId = -1;

    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer == null) return;

            if (exoPlayer.isPlaying()) {
                int currentPosition = (int) exoPlayer.getCurrentPosition();
                int totalDuration = (int) exoPlayer.getDuration();
                globalCurrentPosition = currentPosition;
                globalTotalDuration = totalDuration > 0 ? totalDuration : 0;

                SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                long realListeningSeconds = prefs.getLong("real_listening_seconds", 0);
                prefs.edit().putLong("real_listening_seconds", realListeningSeconds + 1).apply();

                if (!isHistoryRecorded && currentPosition >= 10000) {
                    isHistoryRecorded = true;
                    if (currentSong != null) recordListeningHistoryRealtime(currentSong.getId());
                }

                if (!isFetchingMore && !hasPreloadedForCurrentSong && !isRepeat && totalDuration > 0 && (totalDuration - currentPosition <= 10000)) {
                    if (currentPositionInList == currentSongList.size() - 1) {
                        if (!exoPlayer.isLoading() && System.currentTimeMillis() - lastPreloadTime > 15000) {
                            lastPreloadTime = System.currentTimeMillis();
                            hasPreloadedForCurrentSong = true;
                            preloadRecommendations();
                        }
                    }
                }

                Intent intent = new Intent(ACTION_UPDATE_PROGRESS);
                intent.setPackage(getPackageName());
                intent.putExtra("EXTRA_CURRENT_POSITION", currentPosition);
                intent.putExtra("EXTRA_TOTAL_DURATION", globalTotalDuration);
                sendBroadcast(intent);

                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSession = new MediaSessionCompat(this, "MusicService");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { resumeSong(); }
            @Override public void onPause() { pauseSong(); }
            @Override public void onSkipToNext() { playNextSong(); }
            @Override public void onSkipToPrevious() { playPrevSong(); }
            @Override public void onSeekTo(long pos) { if(exoPlayer != null) exoPlayer.seekTo(pos); }
        });
        mediaSession.setActive(true);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.setAudioAttributes(audioAttributes, true);
        exoPlayer.setWakeMode(C.WAKE_MODE_NETWORK);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    globalTotalDuration = (int) exoPlayer.getDuration();
                    broadcastUIUpdate();
                    showNotification();

                    handler.removeCallbacks(progressRunnable);
                    if (exoPlayer.isPlaying()) {
                        handler.post(progressRunnable);
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    handler.removeCallbacks(progressRunnable);

                    if (stopAfterCurrentTrack) {
                        stopAfterCurrentTrack = false;
                        pauseSong();
                        return;
                    }

                    if (isRepeat) {
                        exoPlayer.seekTo(0);
                        exoPlayer.play();
                        isHistoryRecorded = false;
                        broadcastUIUpdate();
                        updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING);
                    } else {
                        playNextSong();
                    }
                } else if (playbackState == Player.STATE_IDLE) {
                    handler.removeCallbacks(progressRunnable);
                }
            }
            @Override
            public void onPlayerError(@NonNull com.google.android.exoplayer2.PlaybackException error) {
                Log.e("MusicService", "ExoPlayer Error: " + error.getMessage());

                handler.removeCallbacks(progressRunnable);
                globalIsPlaying = false;
                updateMediaSessionState(PlaybackStateCompat.STATE_ERROR);
                broadcastUIUpdate();
                showNotification();

                Toast.makeText(getApplicationContext(), "Lỗi tải nhạc, đang chuyển bài...", Toast.LENGTH_SHORT).show();

                if (exoPlayer != null) {
                    exoPlayer.stop();
                    exoPlayer.clearMediaItems();
                }
                playNextSong();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_PLAY:
                    startPosition = intent.getIntExtra("EXTRA_START_POSITION", 0);
                    if (intent.hasExtra("EXTRA_SONG_LIST")) {
                        currentSongList = (List<Song>) intent.getSerializableExtra("EXTRA_SONG_LIST");
                        currentPositionInList = intent.getIntExtra("EXTRA_SONG_POSITION", 0);
                        if (currentSongList != null && !currentSongList.isEmpty()) {
                            handlePlayRequest(currentSongList.get(currentPositionInList));
                        }
                    } else if (intent.hasExtra("EXTRA_SONG")) {
                        Song song = (Song) intent.getSerializableExtra("EXTRA_SONG");
                        if (song != null) {
                            currentSongList = new ArrayList<>();
                            currentSongList.add(song);
                            currentPositionInList = 0;
                            handlePlayRequest(song);
                        }
                    }
                    break;
                case ACTION_PAUSE: pauseSong(); break;
                case ACTION_RESUME: resumeSong(); break;
                case ACTION_NEXT: playNextSong(); break;
                case ACTION_PREV: playPrevSong(); break;
                case ACTION_SEEK:
                    if (exoPlayer != null) exoPlayer.seekTo(intent.getIntExtra("EXTRA_SEEK_POSITION", 0));
                    break;
                case ACTION_TOGGLE_SHUFFLE:
                    isShuffle = !isShuffle;
                    globalIsShuffle = isShuffle;
                    broadcastUIUpdate();
                    break;
                case ACTION_TOGGLE_REPEAT:
                    isRepeat = !isRepeat;
                    globalIsRepeat = isRepeat;
                    broadcastUIUpdate();
                    break;
                case ACTION_SET_SLEEP_TIMER:
                    int sleepMins = intent.getIntExtra("EXTRA_SLEEP_MINUTES", 0);
                    if (sleepMins == -1) stopAfterCurrentTrack = true;
                    else {
                        stopAfterCurrentTrack = false;
                        setupSleepTimer(sleepMins);
                    }
                    break;
                case ACTION_CHANGE_SPEED:
                    currentSpeed = intent.getFloatExtra("EXTRA_EXO_VALUE", 1.0f);
                    applyExoParameters();
                    break;
                case ACTION_START_ZEN_MODE:
                    int zenMins = intent.getIntExtra("EXTRA_ZEN_MINUTES", 25);
                    startZenModeTimer(zenMins);
                    break;
                case ACTION_STOP_ZEN_MODE: stopZenModeTimer(); break;
            }
        }
        return START_NOT_STICKY;
    }

    private void handlePlayRequest(Song songToPlay) {
        if (currentSong != null && currentSong.getId() == songToPlay.getId()) {
            if (exoPlayer != null && !exoPlayer.isPlaying()) resumeSongWithFade();
            else broadcastUIUpdate();
        } else {
            playSong(songToPlay);
        }
    }

    private void playSong(Song song) {
        synchronized (this) {
            if (isSwitchingSong) return;
            isSwitchingSong = true;
        }

        if (exoPlayer != null && exoPlayer.isPlaying()) {
            fadeOutVolume(() -> {
                try { executePlaySong(song); } finally { isSwitchingSong = false; }
            });
        } else {
            try { executePlaySong(song); } finally { isSwitchingSong = false; }
        }
    }

    private void executePlaySong(Song song) {
        currentSong = song;
        globalCurrentSong = song;
        isHistoryRecorded = false;
        hasPreloadedForCurrentSong = false;
        globalCurrentPosition = 0;
        globalTotalDuration = song.getDuration() > 0 ? song.getDuration() * 1000 : 0;

        Intent progressIntent = new Intent(ACTION_UPDATE_PROGRESS);
        progressIntent.setPackage(getPackageName());
        progressIntent.putExtra("EXTRA_CURRENT_POSITION", 0);
        progressIntent.putExtra("EXTRA_TOTAL_DURATION", globalTotalDuration);
        sendBroadcast(progressIntent);

        try {
            String audioSource = (song.isOffline() && song.getLocalFilePath() != null) ? song.getLocalFilePath() : Utils.normalizeUrl(song.getFilePath());
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(audioSource)));

            if (startPosition > 0) {
                exoPlayer.seekTo(startPosition);
                startPosition = 0;
            }

            exoPlayer.prepare();
            applyExoParameters();

            fadeHandler.removeCallbacksAndMessages(null);
            exoPlayer.setVolume(0f);
            exoPlayer.play();
            fadeInVolume();

            updateGlobalState();
            updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING);
            broadcastUIUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playNextSong() {
        if (currentSongList == null || currentSongList.isEmpty()) return;
        if (isShuffle) {
            int newIndex;
            do { newIndex = (int) (Math.random() * currentSongList.size()); } while (newIndex == currentPositionInList && currentSongList.size() > 1);
            currentPositionInList = newIndex;
            playSong(currentSongList.get(currentPositionInList));
        } else {
            if (currentPositionInList < currentSongList.size() - 1) {
                currentPositionInList++;
                playSong(currentSongList.get(currentPositionInList));
            } else {
                fetchAndAppendRecommendationsAndPlay();
            }
        }
    }

    private void preloadRecommendations() {
        if (isFetchingMore) return;
        isFetchingMore = true;
        RetrofitClient.getApiService().getRecommendations(1, 20).enqueue(new Callback<List<Song>>() {
            @Override
            public void onResponse(@NonNull Call<List<Song>> call, @NonNull Response<List<Song>> response) {
                if (exoPlayer == null) return;

                isFetchingMore = false;
                if (response.isSuccessful() && response.body() != null) filterAndAppendSongs(response.body());
            }
            @Override
            public void onFailure(@NonNull Call<List<Song>> call, @NonNull Throwable t) {
                if (exoPlayer == null) return;

                isFetchingMore = false;
                handler.postDelayed(() -> hasPreloadedForCurrentSong = false, 5000);
            }
        });
    }

    private void fetchAndAppendRecommendationsAndPlay() {
        if (isFetchingMore) return;
        isFetchingMore = true;
        Toast.makeText(this, "Đang tìm bài hát tương tự...", Toast.LENGTH_SHORT).show();
        RetrofitClient.getApiService().getRecommendations(1, 20).enqueue(new Callback<List<Song>>() {
            @Override
            public void onResponse(@NonNull Call<List<Song>> call, @NonNull Response<List<Song>> response) {
                if (exoPlayer == null) return;

                isFetchingMore = false;
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    if (filterAndAppendSongs(response.body())) {
                        if (currentPositionInList + 1 < currentSongList.size()) {
                            currentPositionInList++;
                            playSong(currentSongList.get(currentPositionInList));
                        } else handleFallbackEmptyAPI();
                    } else handleFallbackEmptyAPI();
                } else handleFallbackEmptyAPI();
            }
            @Override
            public void onFailure(@NonNull Call<List<Song>> call, @NonNull Throwable t) {
                if (exoPlayer == null) return;

                isFetchingMore = false;
                handleFallbackEmptyAPI();
            }
        });
    }

    private boolean filterAndAppendSongs(List<Song> newSongs) {
        boolean added = false;
        for (Song newSong : newSongs) {
            boolean exists = false;
            for (Song oldSong : currentSongList) {
                if (oldSong.getId() == newSong.getId()) { exists = true; break; }
            }
            if (!exists) { currentSongList.add(newSong); added = true; }
        }
        return added;
    }

    private void handleFallbackEmptyAPI() {
        Toast.makeText(MusicService.this, "Đang phát lại từ đầu...", Toast.LENGTH_SHORT).show();
        currentPositionInList = 0;
        if (currentSongList != null && !currentSongList.isEmpty()) playSong(currentSongList.get(0));
    }

    private void fadeInVolume() {
        fadeHandler.removeCallbacksAndMessages(null);
        final long stepDelay = 50;
        final float volumeStep = 1.0f / 10;
        fadeHandler.post(new Runnable() {
            float currentVol = 0f;
            @Override
            public void run() {
                currentVol += volumeStep;
                if (currentVol < 1.0f) {
                    if (exoPlayer != null) exoPlayer.setVolume(currentVol);
                    fadeHandler.postDelayed(this, stepDelay);
                } else { if (exoPlayer != null) exoPlayer.setVolume(1.0f); }
            }
        });
    }

    private void fadeOutVolume(Runnable onComplete) {
        fadeHandler.removeCallbacksAndMessages(null);
        final long stepDelay = 50;
        fadeHandler.post(new Runnable() {
            float currentVol = exoPlayer != null ? exoPlayer.getVolume() : 1.0f;
            final float volumeStep = currentVol / 10;
            @Override
            public void run() {
                currentVol -= volumeStep;
                if (currentVol > 0f) {
                    if (exoPlayer != null) exoPlayer.setVolume(currentVol);
                    fadeHandler.postDelayed(this, stepDelay);
                } else {
                    if (exoPlayer != null) exoPlayer.setVolume(0f);
                    if (onComplete != null) onComplete.run();
                }
            }
        });
    }

    private void playPrevSong() {
        if (currentSongList != null && !currentSongList.isEmpty()) {
            if (isShuffle) {
                int newIndex;
                do { newIndex = (int) (Math.random() * currentSongList.size()); } while (newIndex == currentPositionInList && currentSongList.size() > 1);
                currentPositionInList = newIndex;
            } else if (currentPositionInList > 0) {
                currentPositionInList--;
            } else {
                currentPositionInList = currentSongList.size() - 1;
            }
            playSong(currentSongList.get(currentPositionInList));
        }
    }

    private void pauseSong() {
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();

            handler.removeCallbacks(progressRunnable);
            fadeHandler.removeCallbacksAndMessages(null);

            isSwitchingSong = false;

            exoPlayer.setVolume(1.0f);
            updateGlobalState();
            updateMediaSessionState(PlaybackStateCompat.STATE_PAUSED);
            broadcastUIUpdate();
            showNotification();
        }
    }

    private void resumeSong() {
        if (exoPlayer != null && !exoPlayer.isPlaying()) {
            exoPlayer.play();

            handler.removeCallbacks(progressRunnable);
            handler.post(progressRunnable);

            updateGlobalState();
            updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING);
            broadcastUIUpdate();
            showNotification();
        }
    }

    private void resumeSongWithFade() {
        if (exoPlayer != null && !exoPlayer.isPlaying()) {
            fadeHandler.removeCallbacksAndMessages(null);

            isSwitchingSong = false;

            exoPlayer.setVolume(0f);
            exoPlayer.play();

            handler.removeCallbacks(progressRunnable);
            handler.post(progressRunnable);

            fadeInVolume();
            updateGlobalState();
            updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING);
            broadcastUIUpdate();
            showNotification();
        }
    }

    private void applyExoParameters() {
        if (exoPlayer != null) {
            exoPlayer.setPlaybackParameters(new PlaybackParameters(currentSpeed, 1.0f));
            updateMediaSessionState(globalIsPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
        }
    }

    private void updateMediaSessionState(int state) {
        long position = exoPlayer != null ? exoPlayer.getCurrentPosition() : PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(state, position, currentSpeed).build());
    }

    private void updateMediaSessionMetadata(Bitmap coverBitmap) {
        if (currentSong == null) return;
        MediaMetadataCompat.Builder mb = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSong.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSong.getArtist());
        if (coverBitmap != null) mb.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap);
        mediaSession.setMetadata(mb.build());
    }

    private void showNotification() {
        if (currentSong == null) return;

        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent piPrev = PendingIntent.getService(this, 1, new Intent(this, MusicService.class).setAction(ACTION_PREV), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent piPlay = PendingIntent.getService(this, 2, new Intent(this, MusicService.class).setAction(globalIsPlaying ? ACTION_PAUSE : ACTION_RESUME), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent piNext = PendingIntent.getService(this, 3, new Intent(this, MusicService.class).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MyApplication.CHANNEL_ID)
                .setContentTitle(currentSong.getTitle())
                .setContentText(currentSong.getArtist())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(globalIsPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(R.drawable.ic_prev_white, "Prev", piPrev)
                .addAction(globalIsPlaying ? R.drawable.ic_pause_white : R.drawable.ic_play_white, "Play/Pause", piPlay)
                .addAction(R.drawable.ic_next_white, "Next", piNext)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2).setMediaSession(mediaSession.getSessionToken()));

        if (cachedSongId == currentSong.getId() && cachedNotificationBitmap != null) {
            builder.setLargeIcon(cachedNotificationBitmap);
            updateMediaSessionMetadata(cachedNotificationBitmap);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(1, builder.build());
        } else {
            builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.default_cover));
            updateMediaSessionMetadata(null);

            if (!isForeground) {
                startForeground(1, builder.build());
                isForeground = true;
            } else {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(1, builder.build());
            }

            if (currentSong.getCoverArtPath() != null && !currentSong.getCoverArtPath().isEmpty()) {
                if (notificationTarget != null) GlideHelper.clearTarget(getApplicationContext(), notificationTarget);

                final String trackCoverAtRequestTime = currentSong.getCoverArtPath();
                notificationTarget = new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        if (currentSong != null && trackCoverAtRequestTime.equals(currentSong.getCoverArtPath())) {
                            cachedSongId = currentSong.getId();

                            Bitmap scaledBitmap = Bitmap.createScaledBitmap(resource, 256, 256, true);
                            cachedNotificationBitmap = scaledBitmap;

                            builder.setLargeIcon(scaledBitmap);
                            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                            if (nm != null) nm.notify(1, builder.build());
                            updateMediaSessionMetadata(scaledBitmap);
                        }
                    }
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) { }
                };
                GlideHelper.loadBitmap(getApplicationContext(), currentSong.getCoverArtPath(), notificationTarget);
            }
        }
    }

    private void setupSleepTimer(int minutes) {
        if (sleepTimerRunnable != null) sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
        if (minutes > 0) {
            sleepTimerRunnable = () -> {
                pauseSong();
                Toast.makeText(getApplicationContext(), "Đã hết thời gian hẹn giờ! Tắt nhạc.", Toast.LENGTH_LONG).show();
            };
            sleepTimerHandler.postDelayed(sleepTimerRunnable, minutes * 60 * 1000L);
        }
    }

    private void startZenModeTimer(int minutes) {
        if (zenTimer != null) zenTimer.cancel();
        globalIsZenMode = true;
        broadcastUIUpdate();
        zenTimer = new CountDownTimer(minutes * 60 * 1000L, 1000) {
            @Override
            public void onTick(long millis) {
                sendBroadcast(new Intent(ACTION_UPDATE_ZEN_TIMER).setPackage(getPackageName()).putExtra("EXTRA_ZEN_MILLIS", millis));
            }
            @Override
            public void onFinish() {
                globalIsZenMode = false;
                pauseSong();
                sendBroadcast(new Intent(ACTION_ZEN_MODE_FINISHED).setPackage(getPackageName()));
                broadcastUIUpdate();
            }
        }.start();
    }

    private void stopZenModeTimer() {
        if (zenTimer != null) { zenTimer.cancel(); zenTimer = null; }
        globalIsZenMode = false;
        broadcastUIUpdate();
    }

    private void updateGlobalState() {
        globalCurrentSong = currentSong;
        globalIsPlaying = exoPlayer != null && exoPlayer.isPlaying();
    }

    private void broadcastUIUpdate() {
        if (currentSong == null) return;
        updateGlobalState();
        sendBroadcast(new Intent(ACTION_UPDATE_UI).setPackage(getPackageName())
                .putExtra("EXTRA_SONG", currentSong)
                .putExtra("EXTRA_IS_PLAYING", globalIsPlaying)
                .putExtra("EXTRA_IS_SHUFFLE", isShuffle)
                .putExtra("EXTRA_IS_REPEAT", isRepeat)
                .putExtra("EXTRA_IS_ZEN_MODE", globalIsZenMode));
    }

    private void recordListeningHistoryRealtime(int songId) {
        String username = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).getString("userUsername", "");
        if (username.isEmpty()) return;
        HashMap<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("song_id", songId);
        RetrofitClient.getApiService().addListeningHistory(body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) { }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) { }
        });
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopForeground(true);
        isForeground = false;
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
        globalIsPlaying = false;
        globalCurrentSong = null;
        handler.removeCallbacksAndMessages(null);
        sleepTimerHandler.removeCallbacksAndMessages(null);
        fadeHandler.removeCallbacksAndMessages(null);
        if (notificationTarget != null) GlideHelper.clearTarget(getApplicationContext(), notificationTarget);
        if (zenTimer != null) zenTimer.cancel();
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); }
        stopForeground(true);
        isForeground = false;
    }
}