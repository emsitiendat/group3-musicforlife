package com.example.musicforlife;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WrappedFragment extends Fragment {

    private ImageView imgArtist1, imgArtist2, imgArtist3, imgSummaryArtist;
    private TextView tvMinutes, tvSummaryMinutes, tvTopNames, tvQuote, tvSummaryArtist;
    private View cardContent;
    private View page1, page2, page3;
    private View indicator1, indicator2, indicator3;

    private int currentPage = 0;
    private int totalMinutesGlobal = 0;

    private String top1ArtistName = "", top2ArtistName = "", top3ArtistName = "";
    private List<Song> topSongsForPages = new ArrayList<>();
    private int currentPlayingWrappedSongId = -1;

    private static final String FILE_PROVIDER_AUTHORITY = "com.example.musicforlife.fileprovider";

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wrapped, container, false);

        initViews(view);
        setupClickAndSwipeListeners(view);
        fetchListeningData();

        return view;
    }

    private void initViews(View view) {
        imgArtist1 = view.findViewById(R.id.img_wrapped_artist1);
        imgArtist2 = view.findViewById(R.id.img_wrapped_artist2);
        imgArtist3 = view.findViewById(R.id.img_wrapped_artist3);
        imgSummaryArtist = view.findViewById(R.id.img_summary_artist);

        tvMinutes = view.findViewById(R.id.tv_wrapped_minutes);
        tvTopNames = view.findViewById(R.id.tv_wrapped_top_names);
        tvQuote = view.findViewById(R.id.tv_wrapped_quote);
        tvSummaryMinutes = view.findViewById(R.id.tv_summary_minutes);
        tvSummaryArtist = view.findViewById(R.id.tv_summary_artist);

        cardContent = view.findViewById(R.id.card_wrapped_content);

        page1 = view.findViewById(R.id.page_1_minutes);
        page2 = view.findViewById(R.id.page_2_artists);
        page3 = view.findViewById(R.id.page_3_summary);

        indicator1 = view.findViewById(R.id.indicator_page_1);
        indicator2 = view.findViewById(R.id.indicator_page_2);
        indicator3 = view.findViewById(R.id.indicator_page_3);
    }

    private void setupClickAndSwipeListeners(View view) {
        view.findViewById(R.id.btn_back_wrapped).setOnClickListener(v -> closeWrapped());
        view.findViewById(R.id.btn_wrapped_share).setOnClickListener(v -> shareStory());

        final GestureDetector gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 80;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                float x = e.getRawX();
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                if (x < screenWidth * 0.4f) {
                    previousPage();
                } else {
                    nextPage();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getRawX() - e1.getRawX();

                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) previousPage();
                    else nextPage();
                    return true;
                }
                return false;
            }
        });

        view.setOnTouchListener((v, event) -> {
            v.performClick();
            return gestureDetector.onTouchEvent(event);
        });

        imgArtist2.setOnClickListener(v -> navigateToArtist(top1ArtistName));
        imgArtist1.setOnClickListener(v -> navigateToArtist(top2ArtistName));
        imgArtist3.setOnClickListener(v -> navigateToArtist(top3ArtistName));

        imgSummaryArtist.setOnClickListener(v -> navigateToArtist(top1ArtistName));
        tvSummaryArtist.setOnClickListener(v -> navigateToArtist(top1ArtistName));
        tvTopNames.setOnClickListener(v -> navigateToArtist(top1ArtistName));
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updatePageState();
        }
    }

    private void nextPage() {
        if (currentPage < 2) {
            currentPage++;
            updatePageState();
        }
    }

    private void closeWrapped() {
        if (getActivity() != null) {
            requireActivity().getSupportFragmentManager().popBackStack();
        }
    }

    private void navigateToArtist(String artistName) {
        if (artistName == null || artistName.isEmpty()) return;
        if (getActivity() instanceof MainActivity) {
            ArtistFragment artistFragment = ArtistFragment.newInstance(artistName);
            ((MainActivity) getActivity()).navigateToDetailFragment(artistFragment, "ArtistFragment");
        }
    }

    private void fetchListeningData() {
        if (getActivity() == null) return;
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");

        if (username.isEmpty()) {
            Toast.makeText(getContext(), "Vui lòng đăng nhập!", Toast.LENGTH_SHORT).show();
            closeWrapped();
            return;
        }

        RetrofitClient.getApiService().getListeningHistory(username).enqueue(new Callback<List<Song>>() {
            @Override
            public void onResponse(@NonNull Call<List<Song>> call, @NonNull Response<List<Song>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    calculateTopStats(response.body());
                } else {
                    tvMinutes.setText("0");
                    tvQuote.setText("Hãy nghe nhạc nhiều hơn nhé!");
                }
            }
            @Override
            public void onFailure(@NonNull Call<List<Song>> call, @NonNull Throwable t) {
                if (getContext() != null) Toast.makeText(getContext(), "Lỗi mạng!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void calculateTopStats(List<Song> historyList) {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        long realSeconds = prefs.getLong("real_listening_seconds", 0);

        new Thread(() -> {
            HashMap<String, Integer> artistCountMap = new HashMap<>();
            HashMap<String, String> artistCoverMap = new HashMap<>();

            HashMap<String, Integer> songCountMap = new HashMap<>();
            HashMap<String, Song> uniqueSongsMap = new HashMap<>();


            for (Song song : historyList) {

                String artist = song.getArtist();
                artistCountMap.put(artist, artistCountMap.getOrDefault(artist, 0) + 1);
                if (!artistCoverMap.containsKey(artist) && song.getCoverArtPath() != null) {
                    artistCoverMap.put(artist, song.getCoverArtPath());
                }

                String songId = String.valueOf(song.getId());
                songCountMap.put(songId, songCountMap.getOrDefault(songId, 0) + 1);
                if (!uniqueSongsMap.containsKey(songId)) {
                    uniqueSongsMap.put(songId, song);
                }
            }

            totalMinutesGlobal = (int) (realSeconds / 60);

            if (totalMinutesGlobal == 0 && realSeconds > 0) {
                totalMinutesGlobal = 1;
            }

            List<Map.Entry<String, Integer>> sortedArtists = new ArrayList<>(artistCountMap.entrySet());
            sortedArtists.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

            List<Map.Entry<String, Integer>> sortedSongs = new ArrayList<>(songCountMap.entrySet());
            sortedSongs.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

            List<Song> extractedTopSongs = new ArrayList<>();
            for (int i = 0; i < Math.min(3, sortedSongs.size()); i++) {
                extractedTopSongs.add(uniqueSongsMap.get(sortedSongs.get(i).getKey()));
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    topSongsForPages = extractedTopSongs;
                    setupDataUI(sortedArtists, artistCoverMap);
                    updatePageState();
                });
            }
        }).start();
    }
    private void setupDataUI(List<Map.Entry<String, Integer>> sortedEntries, HashMap<String, String> artistCoverMap) {
        if (getContext() == null || sortedEntries.isEmpty()) return;

        top1ArtistName = sortedEntries.get(0).getKey();
        String top1CoverUrl = artistCoverMap.get(top1ArtistName);

        tvSummaryArtist.setText(top1ArtistName);
        StringBuilder names = new StringBuilder(top1ArtistName);

        GlideHelper.loadCenterCrop(this, top1CoverUrl, imgArtist2);

        GlideHelper.loadAvatar(this, top1CoverUrl, imgSummaryArtist);

        if (sortedEntries.size() > 1) {
            top2ArtistName = sortedEntries.get(1).getKey();
            String top2CoverUrl = artistCoverMap.get(top2ArtistName);

            GlideHelper.loadCenterCrop(this, top2CoverUrl, imgArtist1);
            names.append(", ").append(top2ArtistName);
        } else {
            imgArtist1.setVisibility(View.GONE);
        }

        if (sortedEntries.size() > 2) {
            top3ArtistName = sortedEntries.get(2).getKey();
            String top3CoverUrl = artistCoverMap.get(top3ArtistName);

            GlideHelper.loadCenterCrop(this, top3CoverUrl, imgArtist3);
            names.append(", ").append(top3ArtistName);
        } else {
            imgArtist3.setVisibility(View.GONE);
        }

        tvTopNames.setText(names.toString());
    }

    private void updatePageState() {
        indicator1.setBackgroundColor(currentPage >= 0 ? Color.WHITE : Color.parseColor("#4DFFFFFF"));
        indicator2.setBackgroundColor(currentPage >= 1 ? Color.WHITE : Color.parseColor("#4DFFFFFF"));
        indicator3.setBackgroundColor(currentPage >= 2 ? Color.WHITE : Color.parseColor("#4DFFFFFF"));

        page1.setVisibility(currentPage == 0 ? View.VISIBLE : View.GONE);
        page2.setVisibility(currentPage == 1 ? View.VISIBLE : View.GONE);
        page3.setVisibility(currentPage == 2 ? View.VISIBLE : View.GONE);

        if (currentPage == 0) {
            animateNumber(tvMinutes, totalMinutesGlobal);
            setQuote(totalMinutesGlobal);
        } else if (currentPage == 1) {
            animateAvatarBounce(imgArtist2, 0);
            if (imgArtist1.getVisibility() == View.VISIBLE) animateAvatarBounce(imgArtist1, 150);
            if (imgArtist3.getVisibility() == View.VISIBLE) animateAvatarBounce(imgArtist3, 300);
        } else if (currentPage == 2) {
            tvSummaryMinutes.setText(String.format("%,d phút", totalMinutesGlobal).replace(',', '.'));
            cardContent.setAlpha(0f);
            cardContent.setTranslationY(100f);
            cardContent.animate().alpha(1f).translationY(0f).setDuration(500).setInterpolator(new DecelerateInterpolator()).start();
        }

        playSongForCurrentPage();
    }

    private void playSongForCurrentPage() {
        if (topSongsForPages == null || topSongsForPages.isEmpty()) return;

        int songIndex = Math.min(currentPage, topSongsForPages.size() - 1);
        Song targetSong = topSongsForPages.get(songIndex);

        if (currentPlayingWrappedSongId != targetSong.getId()) {
            currentPlayingWrappedSongId = targetSong.getId();
            playTopSongAudio(targetSong);
        }
    }

    private void playTopSongAudio(Song song) {
        if (song == null || getContext() == null) return;

        Intent playIntent = new Intent(getContext(), MusicService.class);
        playIntent.setAction(MusicService.ACTION_PLAY);
        playIntent.putExtra("EXTRA_SONG", song);
        requireContext().startService(playIntent);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (getContext() != null) {
                Intent seekIntent = new Intent(getContext(), MusicService.class);
                seekIntent.setAction(MusicService.ACTION_SEEK);
                seekIntent.putExtra("EXTRA_SEEK_POSITION", 30000);
                getContext().startService(seekIntent);
            }
        }, 1000);
    }

    private void animateNumber(TextView textView, int target) {
        ValueAnimator animator = ValueAnimator.ofInt(0, target);
        animator.setDuration(1500);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            textView.setText(String.format("%,d", (int) animation.getAnimatedValue()).replace(',', '.'));
        });
        animator.start();
    }

    private void animateAvatarBounce(View view, int delay) {
        view.setScaleX(0f);
        view.setScaleY(0f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(600).setStartDelay(delay).setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    private void setQuote(int totalMinutes) {
        if (totalMinutes < 100) tvQuote.setText("Bạn là lính mới!");
        else if (totalMinutes < 500) tvQuote.setText("Người yêu nhạc đích thực!");
        else if (totalMinutes < 1000) tvQuote.setText("Đích thị là fan cuồng!");
        else tvQuote.setText("Âm nhạc chảy trong máu bạn!");
    }

    private void shareStory() {
        if (getContext() == null || getActivity() == null) return;

        View btnShare = getView().findViewById(R.id.btn_wrapped_share);
        btnShare.setVisibility(View.INVISIBLE);

        Bitmap bitmap = Bitmap.createBitmap(cardContent.getWidth(), cardContent.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        cardContent.draw(canvas);

        btnShare.setVisibility(View.VISIBLE);

        try {
            File cachePath = new File(requireContext().getCacheDir(), "images");
            cachePath.mkdirs();
            File imageFile = new File(cachePath, "wrapped_share.png");
            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(requireContext(), FILE_PROVIDER_AUTHORITY, imageFile);
            if (contentUri != null) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/png");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Chia sẻ lên Story"));
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Lỗi chia sẻ!", Toast.LENGTH_SHORT).show();
        }
    }
}