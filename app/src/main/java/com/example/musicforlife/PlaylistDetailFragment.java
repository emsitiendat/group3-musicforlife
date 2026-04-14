package com.example.musicforlife;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlaylistDetailFragment extends Fragment {

    private ImageView imgCover;
    private View layoutCollage;
    private ImageView imgCollage1, imgCollage2, imgCollage3, imgCollage4;

    private TextView tvName;
    private TextView tvPlaylistDescription;
    private TextView tvToolbarTitle;
    private TextView tvPlaylistStats;
    private ShapeableImageView imgCreatorAvatar;
    private LinearLayout layoutCreatorProfile;
    private TextView btnFollowCreator;

    private AppBarLayout appBarLayout;
    private ImageButton btnBack;
    private ImageButton btnOptions;

    private ImageButton btnPlaylistHeart;
    private ImageButton btnPlaylistComment;
    private ImageButton btnPlaylistShare;
    private TextView btnPostCommunity;
    private FloatingActionButton btnPlayRandom;

    private ImageButton btnSearchSong;
    private LinearLayout layoutSearchBarDetail;
    private EditText etSearchSong;
    private ImageButton btnCloseSearchDetail;

    private RecyclerView rvSongs;
    private SongAdapter songAdapter;

    private LinearLayout layoutGlobalLoading;
    private LinearLayout layoutQuickAddSection;
    private RecyclerView rvQuickAddSongs;
    private QuickAddAdapter quickAddAdapter;

    private int quickAddPage = 1;
    private boolean isFetchingQuickAdd = false;

    private int playlistId = -1;
    private String playlistName = "";
    private String playlistCover = "";
    private String playlistDescription = "";

    private List<Song> fullSongsList = new ArrayList<>();
    private List<Song> currentSongsList = new ArrayList<>();

    private boolean isFavoritePlaylist = false;
    private boolean isOwner = false;
    private boolean isPlaylistSaved = false;
    private boolean isCreatorFollowed = false;
    private boolean isCustomCover = false;

    private android.net.Uri selectedImageUri = null;
    private ImageView currentPreviewImageView = null;

    private boolean isBlend = false;
    private String ownerAvatarUrl;
    private String partnerAvatarUrl;
    private String partnerUsername;
    private View layoutDualAvatar;
    private ShapeableImageView imgOwner, imgPartner;
    private String ownerUsername;

    private final androidx.activity.result.ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (currentPreviewImageView != null && selectedImageUri != null) {
                        currentPreviewImageView.setImageURI(selectedImageUri);
                        currentPreviewImageView.setBackgroundTintList(null);
                    }
                }
            });

    private java.io.File getFileFromUri(android.net.Uri uri) {
        try {
            java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            java.io.File tempFile = new java.io.File(requireContext().getCacheDir(), "temp_edit_cover.jpg");
            java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            return tempFile;
        } catch (Exception e) {
            return null;
        }
    }

    private final BroadcastReceiver playlistReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (MusicService.ACTION_UPDATE_UI.equals(action)) {
                Song realSong = (Song) intent.getSerializableExtra("EXTRA_SONG");
                if (realSong != null) {
                    MusicService.globalCurrentSong = realSong;
                    if (songAdapter != null) songAdapter.notifyDataSetChanged();
                    if (quickAddAdapter != null) quickAddAdapter.updatePlayingState(realSong.getId());
                }
            } else if ("com.example.musicforlife.ACTION_FAVORITE_CHANGED".equals(action)) {
                if (isFavoritePlaylist) {
                    Song changedSong = (Song) intent.getSerializableExtra("EXTRA_SONG");
                    boolean isLiked = intent.getBooleanExtra("EXTRA_IS_LIKED", false);
                    boolean hasChanged = false;

                    if (changedSong != null) {
                        if (!isLiked) {
                            for (int i = 0; i < currentSongsList.size(); i++) {
                                if (currentSongsList.get(i).getId() == changedSong.getId()) {
                                    currentSongsList.remove(i);
                                    hasChanged = true;
                                    break;
                                }
                            }
                            for (int i = 0; i < fullSongsList.size(); i++) {
                                if (fullSongsList.get(i).getId() == changedSong.getId()) {
                                    fullSongsList.remove(i);
                                    hasChanged = true;
                                    break;
                                }
                            }
                        } else {
                            boolean exists = false;
                            for (Song s : currentSongsList) {
                                if (s.getId() == changedSong.getId()) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                currentSongsList.add(0, changedSong);
                                hasChanged = true;
                            }

                            boolean existsInFull = false;
                            for (Song s : fullSongsList) {
                                if (s.getId() == changedSong.getId()) {
                                    existsInFull = true;
                                    break;
                                }
                            }
                            if (!existsInFull) {
                                fullSongsList.add(0, changedSong);
                            }
                        }
                    }

                    updateStatsUIOnly();

                    if (hasChanged && songAdapter != null) {
                        songAdapter.updateData(currentSongsList);
                        songAdapter.notifyDataSetChanged();
                    }
                } else {
                    if (songAdapter != null) songAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist_detail, container, false);

        imgCover = view.findViewById(R.id.img_detail_cover);
        layoutCollage = view.findViewById(R.id.layout_collage_cover);
        imgCollage1 = view.findViewById(R.id.img_collage_1);
        imgCollage2 = view.findViewById(R.id.img_collage_2);
        imgCollage3 = view.findViewById(R.id.img_collage_3);
        imgCollage4 = view.findViewById(R.id.img_collage_4);

        layoutDualAvatar = view.findViewById(R.id.layout_dual_avatar);
        imgOwner = view.findViewById(R.id.img_blend_owner);
        imgPartner = view.findViewById(R.id.img_blend_partner);

        tvName = view.findViewById(R.id.tv_detail_name);
        tvToolbarTitle = view.findViewById(R.id.tv_toolbar_title);

        btnSearchSong = view.findViewById(R.id.btn_search_song);
        layoutSearchBarDetail = view.findViewById(R.id.layout_search_bar_detail);
        etSearchSong = view.findViewById(R.id.et_search_song);
        btnCloseSearchDetail = view.findViewById(R.id.btn_close_search_detail);

        tvPlaylistDescription = view.findViewById(R.id.tv_playlist_description);
        tvPlaylistStats = view.findViewById(R.id.tv_playlist_stats);
        imgCreatorAvatar = view.findViewById(R.id.img_creator_avatar);
        layoutCreatorProfile = view.findViewById(R.id.layout_creator_profile);
        btnFollowCreator = view.findViewById(R.id.btn_follow_creator);
        btnPlaylistComment = view.findViewById(R.id.btn_playlist_comment);
        btnPlaylistShare = view.findViewById(R.id.btn_playlist_share);
        btnPostCommunity = view.findViewById(R.id.btn_post_community);

        appBarLayout = view.findViewById(R.id.appbar_playlist);
        btnBack = view.findViewById(R.id.btn_back_detail);
        btnOptions = view.findViewById(R.id.btn_playlist_options);
        btnPlaylistHeart = view.findViewById(R.id.btn_playlist_heart);
        btnPlayRandom = view.findViewById(R.id.btn_play_random);
        rvSongs = view.findViewById(R.id.rv_playlist_songs);

        layoutGlobalLoading = view.findViewById(R.id.layout_global_loading);
        layoutQuickAddSection = view.findViewById(R.id.layout_quick_add_section);
        rvQuickAddSongs = view.findViewById(R.id.rv_quick_add_songs);

        setupScrollAnimation();
        setupSocialListeners();
        setupSearchLogic();

        if (rvQuickAddSongs != null) {
            rvQuickAddSongs.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            quickAddAdapter = new QuickAddAdapter(getContext(), new ArrayList<>(),
                    (song, position) -> addSongToPlaylist(song, position),
                    (songs, position) -> {
                        Intent playIntent = new Intent(getContext(), MusicService.class);
                        playIntent.setAction(MusicService.ACTION_PLAY);
                        playIntent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(songs));
                        playIntent.putExtra("EXTRA_SONG_POSITION", position);
                        requireContext().startService(playIntent);
                    },
                    artistName -> {
                        Intent intent = new Intent(getContext(), MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        intent.putExtra("open_artist_detail", true);
                        intent.putExtra("artist_name", artistName);
                        startActivity(intent);
                    }
            );
            rvQuickAddSongs.setAdapter(quickAddAdapter);
        }

        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        rvSongs.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSongs.setItemAnimator(null);

        songAdapter = new SongAdapter(getContext(), currentSongsList, (songs, position) -> {
            Intent playIntent = new Intent(getContext(), MusicService.class);
            playIntent.setAction(MusicService.ACTION_PLAY);
            playIntent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(songs));
            playIntent.putExtra("EXTRA_SONG_POSITION", position);
            requireContext().startService(playIntent);
        });
        rvSongs.setAdapter(songAdapter);

        Bundle args = getArguments();
        if (args != null) {
            isFavoritePlaylist = args.getBoolean("is_favorite_playlist", false);
            if (isFavoritePlaylist) {
                setupFavoritePlaylistUI();
            } else {
                playlistId = args.getInt("EXTRA_PLAYLIST_ID", -1);
                playlistName = args.getString("EXTRA_PLAYLIST_NAME");
                playlistCover = args.getString("EXTRA_PLAYLIST_COVER", "");
                isCustomCover = args.getBoolean("EXTRA_IS_CUSTOM_COVER", false);

                isBlend = args.getBoolean("EXTRA_IS_BLEND", false);
                ownerAvatarUrl = args.getString("EXTRA_OWNER_AVATAR");
                partnerAvatarUrl = args.getString("EXTRA_PARTNER_AVATAR");
                ownerUsername = args.getString("EXTRA_OWNER_USERNAME");
                partnerUsername = args.getString("EXTRA_PARTNER_USERNAME");

                if (playlistName == null || playlistName.isEmpty()) playlistName = "Playlist của bạn";
                tvName.setText(playlistName);
                tvToolbarTitle.setText(playlistName);

                if (isBlend) {
                    setupBlendUI();
                } else if (isCustomCover && playlistCover != null && !playlistCover.isEmpty() && !playlistCover.equals("null")) {
                    imgCover.setVisibility(View.VISIBLE);
                    layoutCollage.setVisibility(View.GONE);
                    GlideHelper.loadCenterCrop(this, playlistCover, imgCover);
                }

                if (playlistId != -1) {
                    checkPlaylistOwnershipAndSetupOptions();
                }
            }
        }

        if (btnPlayRandom != null) {
            btnPlayRandom.setOnClickListener(v -> {
                if (currentSongsList != null && !currentSongsList.isEmpty()) {
                    int randomPosition = new Random().nextInt(currentSongsList.size());
                    Intent playIntent = new Intent(getContext(), MusicService.class);
                    playIntent.setAction(MusicService.ACTION_PLAY);
                    playIntent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(currentSongsList));
                    playIntent.putExtra("EXTRA_SONG_POSITION", randomPosition);
                    requireContext().startService(playIntent);
                    Toast.makeText(getContext(), "Đang phát ngẫu nhiên", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Playlist chưa có bài hát nào!", Toast.LENGTH_SHORT).show();
                }
            });
        }
        return view;
    }

    private void setupBlendUI() {
        tvPlaylistDescription.setVisibility(View.VISIBLE);

        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String myUsername = prefs.getString("userUsername", "");

        String myAvatar = "";
        String friendAvatar = "";
        String friendName = "";

        if (myUsername.equals(ownerUsername)) {
            myAvatar = ownerAvatarUrl;
            friendAvatar = partnerAvatarUrl;
            friendName = partnerUsername;
        } else {
            myAvatar = partnerAvatarUrl;
            friendAvatar = ownerAvatarUrl;
            friendName = ownerUsername;
        }

        if (friendName != null && !friendName.isEmpty() && !friendName.equals("null")) {
            tvPlaylistDescription.setText("Bản Mix giữa bạn và " + friendName);
        } else {
            tvPlaylistDescription.setText("Mời bạn bè để tạo bản Mix chung!");
        }

        if (imgCover != null) imgCover.setVisibility(View.GONE);
        if (layoutCollage != null) layoutCollage.setVisibility(View.GONE);

        if (layoutDualAvatar != null) layoutDualAvatar.setVisibility(View.VISIBLE);

        if (imgOwner != null && myAvatar != null && !myAvatar.isEmpty()) {
            GlideHelper.loadAvatar(this, myAvatar, imgOwner);
        }

        if (imgPartner != null) {
            if (friendAvatar != null && !friendAvatar.isEmpty() && !friendAvatar.equals("null") && !friendAvatar.endsWith("default_avatar.png")) {
                GlideHelper.loadAvatar(this, friendAvatar, imgPartner);
                imgPartner.setOnClickListener(null);
            } else {
                imgPartner.setImageResource(R.drawable.ic_add_friend);
                imgPartner.setOnClickListener(v -> openInviteUserSheet());
            }
        }

        if (btnOptions != null) {
            btnOptions.setVisibility(View.GONE);
        }
    }
    private void openInviteUserSheet() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String currentUsername = prefs.getString("userUsername", "");

        UserListBottomSheet bottomSheet = UserListBottomSheet.newInstance("followers", currentUsername);
        bottomSheet.show(getChildFragmentManager(), "InviteUserSheet");
    }

    private void setupSearchLogic() {
        if (btnSearchSong == null || etSearchSong == null) return;

        btnSearchSong.setOnClickListener(v -> {
            if (layoutSearchBarDetail.getVisibility() == View.GONE) {
                layoutSearchBarDetail.setVisibility(View.VISIBLE);
                int accentColor = ContextCompat.getColor(requireContext(), R.color.color_accent);
                btnSearchSong.setColorFilter(accentColor);

                etSearchSong.requestFocus();
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etSearchSong, InputMethodManager.SHOW_IMPLICIT);
            } else {
                btnCloseSearchDetail.performClick();
            }
        });

        btnCloseSearchDetail.setOnClickListener(v -> {
            etSearchSong.setText("");
            layoutSearchBarDetail.setVisibility(View.GONE);

            int secondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);
            btnSearchSong.setColorFilter(secondaryColor);

            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etSearchSong.getWindowToken(), 0);

            currentSongsList.clear();
            currentSongsList.addAll(fullSongsList);
            if (songAdapter != null) {
                songAdapter.updateData(currentSongsList);
                songAdapter.notifyDataSetChanged();
            }
        });

        etSearchSong.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterSongs(s.toString());
            }
        });
    }

    private void filterSongs(String query) {
        if (query.isEmpty()) {
            currentSongsList.clear();
            currentSongsList.addAll(fullSongsList);
            if (songAdapter != null) {
                songAdapter.updateData(currentSongsList);
                songAdapter.notifyDataSetChanged();
            }
            return;
        }

        List<Song> filteredList = new ArrayList<>();
        String normalizedQuery = removeAccents(query).trim();

        for (Song song : fullSongsList) {
            String normalizedTitle = removeAccents(song.getTitle());
            String normalizedArtist = removeAccents(song.getArtist());

            if (normalizedTitle.contains(normalizedQuery) || normalizedArtist.contains(normalizedQuery)) {
                filteredList.add(song);
            }
        }

        currentSongsList.clear();
        currentSongsList.addAll(filteredList);

        if (songAdapter != null) {
            songAdapter.updateData(currentSongsList);
            songAdapter.notifyDataSetChanged();
        }
    }

    private void setupSocialListeners() {
        if (layoutCreatorProfile != null) {
            layoutCreatorProfile.setOnClickListener(v -> {
                if (isOwner) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).switchToAccountTab();
                    }
                } else {
                    Toast.makeText(getContext(), "Tính năng xem trang tác giả khác đang hoàn thiện", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnFollowCreator != null) {
            btnFollowCreator.setOnClickListener(v -> {
                isCreatorFollowed = !isCreatorFollowed;
                if (isCreatorFollowed) {
                    btnFollowCreator.setText("Đang theo dõi");
                    btnFollowCreator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1DB954")));
                } else {
                    btnFollowCreator.setText("Theo dõi");
                    btnFollowCreator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1AFFFFFF")));
                }
            });
        }

        if (btnPlaylistHeart != null) {
            btnPlaylistHeart.setOnClickListener(v -> {
                isPlaylistSaved = !isPlaylistSaved;
                if (isPlaylistSaved) {
                    btnPlaylistHeart.setImageResource(R.drawable.ic_heart_filled);
                    btnPlaylistHeart.setColorFilter(Color.parseColor("#FF5252"));
                    Toast.makeText(getContext(), "Đã lưu Playlist vào Thư viện", Toast.LENGTH_SHORT).show();
                } else {
                    btnPlaylistHeart.setImageResource(R.drawable.ic_heart_outline);
                    btnPlaylistHeart.setColorFilter(Color.parseColor("#9CA3AF"));
                    Toast.makeText(getContext(), "Đã xóa khỏi Thư viện", Toast.LENGTH_SHORT).show();
                }
                btnPlaylistHeart.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150)
                        .withEndAction(() -> btnPlaylistHeart.animate().scaleX(1f).scaleY(1f).setDuration(150).start()).start();
            });
        }

        if (btnPlaylistComment != null) {
            btnPlaylistComment.setOnClickListener(v -> {
                Toast.makeText(getContext(), "Tính năng Bình luận Playlist đang phát triển", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnPlaylistShare != null) {
            btnPlaylistShare.setOnClickListener(v -> {
                Toast.makeText(getContext(), "Mở menu Chia sẻ Playlist", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnPostCommunity != null) {
            btnPostCommunity.setOnClickListener(v -> {
                Toast.makeText(getContext(), "Chuyển đến màn hình Viết bài đăng Cộng đồng", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupFavoritePlaylistUI() {
        isOwner = true;
        songAdapter.setFavoriteView(true);
        tvName.setText("Bài hát yêu thích");
        tvToolbarTitle.setText("Bài hát yêu thích");

        tvPlaylistDescription.setVisibility(View.VISIBLE);
        tvPlaylistDescription.setText("Những giai điệu bạn đã thả tim ❤️");

        imgCover.setVisibility(View.VISIBLE);
        layoutCollage.setVisibility(View.GONE);
        imgCover.setImageResource(R.drawable.ic_heart_filled);
        imgCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imgCover.setPadding(80, 80, 80, 80);

        tvName.setTextColor(Color.parseColor("#FF5252"));
        imgCreatorAvatar.setImageResource(R.drawable.ic_heart_filled);
        imgCreatorAvatar.setImageTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252")));

        if (btnOptions != null) btnOptions.setVisibility(View.GONE);
        if (btnPlaylistHeart != null) btnPlaylistHeart.setVisibility(View.GONE);
        if (btnFollowCreator != null) btnFollowCreator.setVisibility(View.GONE);
        if (btnPostCommunity != null) btnPostCommunity.setVisibility(View.GONE);

        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "Bạn");
        tvPlaylistStats.setText(username + " • " + fullSongsList.size() + " bài hát");

        View.OnClickListener hintListener = v -> Toast.makeText(getContext(), "📌 Kéo trái để xóa bài hát khỏi danh sách Yêu thích!", Toast.LENGTH_SHORT).show();
        tvName.setOnClickListener(hintListener);
        imgCover.setOnClickListener(hintListener);
        setupSwipeAndDrag();
    }

    private void checkPlaylistOwnershipAndSetupOptions() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");
        String userAvatar = prefs.getString("userAvatar", "");

        if (username.isEmpty()) {
            setupViewerMode("Hệ thống", "");
            return;
        }

        RetrofitClient.getApiService().getUserPlaylists(username).enqueue(new Callback<List<Playlist>>() {
            @Override
            public void onResponse(@NonNull Call<List<Playlist>> call, @NonNull Response<List<Playlist>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    isOwner = false;
                    for (Playlist p : response.body()) {
                        if (p.getId() == playlistId) {
                            isOwner = true;
                            playlistDescription = p.getDescription() != null ? p.getDescription() : "";
                            break;
                        }
                    }
                    if (isOwner) {
                        setupOwnerMode(username, userAvatar);
                    } else {
                        setupViewerMode("Tác giả khác", "");
                    }
                } else {
                    setupViewerMode("Hệ thống", "");
                }
            }
            @Override
            public void onFailure(@NonNull Call<List<Playlist>> call, @NonNull Throwable t) {
                setupViewerMode("Hệ thống", "");
            }
        });
    }

    private void setupOwnerMode(String creatorName, String avatarUrl) {
        isOwner = true;
        tvPlaylistStats.setText(creatorName + " • " + currentSongsList.size() + " bài hát");
        if (!avatarUrl.isEmpty() && imgCreatorAvatar != null) {
            GlideHelper.loadAvatar(this, avatarUrl, imgCreatorAvatar);
        }

        tvPlaylistDescription.setVisibility(View.VISIBLE);
        if (!playlistDescription.trim().isEmpty()) {
            tvPlaylistDescription.setText(playlistDescription);
        } else {
            tvPlaylistDescription.setText("Giai điệu mang đậm dấu ấn cá nhân của bạn.");
        }

        if (btnOptions != null) {
            btnOptions.setVisibility(isBlend ? View.GONE : View.VISIBLE);
        }
        if (btnPostCommunity != null) btnPostCommunity.setVisibility(View.VISIBLE);

        if (btnFollowCreator != null) btnFollowCreator.setVisibility(View.GONE);
        if (btnPlaylistHeart != null) btnPlaylistHeart.setVisibility(View.GONE);

        setupPlaylistOptions();
        setupSwipeAndDrag();
    }

    private void setupViewerMode(String creatorName, String avatarUrl) {
        isOwner = false;
        tvPlaylistStats.setText(creatorName + " • " + currentSongsList.size() + " bài hát");
        if (!avatarUrl.isEmpty() && imgCreatorAvatar != null) {
            GlideHelper.loadAvatar(this, avatarUrl, imgCreatorAvatar);
        } else {
            imgCreatorAvatar.setImageResource(R.drawable.default_avatar);
        }

        tvPlaylistDescription.setVisibility(View.VISIBLE);
        tvPlaylistDescription.setText("Tuyển tập âm nhạc tuyệt vời từ " + creatorName);

        if (btnFollowCreator != null) btnFollowCreator.setVisibility(View.VISIBLE);
        if (btnPlaylistHeart != null) btnPlaylistHeart.setVisibility(View.VISIBLE);

        if (btnOptions != null) btnOptions.setVisibility(View.GONE);
        if (btnPostCommunity != null) btnPostCommunity.setVisibility(View.GONE);

        View.OnClickListener hintListener = v -> Toast.makeText(getContext(), "📌 Đây là Playlist của người khác. Bạn không thể Sửa hay Xóa!", Toast.LENGTH_SHORT).show();
        tvName.setOnClickListener(hintListener);
        imgCover.setOnClickListener(hintListener);
    }

    private void updateStatsUIOnly() {
        if (tvPlaylistStats == null || getContext() == null) return;

        String currentText = tvPlaylistStats.getText().toString();
        String creatorName = currentText.split("•")[0].trim();

        if (isFavoritePlaylist) {
            tvPlaylistStats.setText(creatorName + " • " + SongAdapter.globalLikedSongsCache.size() + " bài hát");
        } else {
            tvPlaylistStats.setText(creatorName + " • " + fullSongsList.size() + " bài hát");
        }
    }

    private void updatePlaylistCoverCollage(List<Song> songs) {
        updateStatsUIOnly();

        if (isFavoritePlaylist || isBlend || getContext() == null) return;

        if (isCustomCover && playlistCover != null && !playlistCover.isEmpty() && !playlistCover.equals("null")) {
            imgCover.setVisibility(View.VISIBLE);
            layoutCollage.setVisibility(View.GONE);
            GlideHelper.loadCenterCrop(this, playlistCover, imgCover);
            return;
        }

        if (songs != null && songs.size() >= 4) {
            imgCover.setVisibility(View.GONE);
            layoutCollage.setVisibility(View.VISIBLE);
            GlideHelper.loadCenterCrop(this, songs.get(0).getCoverArtPath(), imgCollage1);
            GlideHelper.loadCenterCrop(this, songs.get(1).getCoverArtPath(), imgCollage2);
            GlideHelper.loadCenterCrop(this, songs.get(2).getCoverArtPath(), imgCollage3);
            GlideHelper.loadCenterCrop(this, songs.get(3).getCoverArtPath(), imgCollage4);
        } else if (songs != null && !songs.isEmpty()) {
            imgCover.setVisibility(View.VISIBLE);
            layoutCollage.setVisibility(View.GONE);
            GlideHelper.loadCenterCrop(this, songs.get(0).getCoverArtPath(), imgCover);
        } else {
            imgCover.setVisibility(View.VISIBLE);
            layoutCollage.setVisibility(View.GONE);
            imgCover.setImageResource(R.drawable.default_cover);
        }
    }

    private void setupScrollAnimation() {
        if (appBarLayout == null || tvToolbarTitle == null) return;
        appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int scrollRange = appBarLayout.getTotalScrollRange();
            if (scrollRange == 0) return;
            float alpha = (float) Math.abs(verticalOffset) / scrollRange;
            if (alpha > 0.8f) {
                if (layoutSearchBarDetail == null || layoutSearchBarDetail.getVisibility() != View.VISIBLE) {
                    float titleAlpha = (alpha - 0.8f) * 5f;
                    tvToolbarTitle.setAlpha(Math.min(titleAlpha, 1f));
                }
            } else {
                tvToolbarTitle.setAlpha(0f);
            }
        });
    }

    private void setupPlaylistOptions() {
        if (btnOptions != null) {
            btnOptions.setOnClickListener(v -> showEditBottomSheet());
        }

        View.OnLongClickListener longClickListener = v -> {
            if(!isBlend) {
                showEditBottomSheet();
            }
            return true;
        };
        tvName.setOnLongClickListener(longClickListener);
        imgCover.setOnLongClickListener(longClickListener);
    }

    private void showEditBottomSheet() {
        if (getContext() == null || isBlend) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_edit_playlist, null);
        dialog.setContentView(view);

        selectedImageUri = null;
        currentPreviewImageView = view.findViewById(R.id.img_edit_preview);

        EditText etName = view.findViewById(R.id.et_edit_name);
        EditText etDesc = view.findViewById(R.id.et_edit_description);
        com.google.android.material.switchmaterial.SwitchMaterial switchPublic = view.findViewById(R.id.switch_edit_public);

        etName.setText(playlistName);
        etName.setSelection(playlistName.length());
        etDesc.setText(playlistDescription);

        if (playlistCover != null && !playlistCover.isEmpty() && !playlistCover.equals("null")) {
            GlideHelper.loadCenterCrop(this, playlistCover, currentPreviewImageView);
            currentPreviewImageView.setBackgroundTintList(null);
        } else if (!fullSongsList.isEmpty()) {
            GlideHelper.loadCenterCrop(this, fullSongsList.get(0).getCoverArtPath(), currentPreviewImageView);
            currentPreviewImageView.setBackgroundTintList(null);
        }

        view.findViewById(R.id.layout_edit_cover).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        view.findViewById(R.id.btn_save_playlist).setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            String newDesc = etDesc.getText().toString().trim();
            boolean isPublic = switchPublic.isChecked();

            if (!newName.isEmpty()) {
                updatePlaylistAPI(newName, newDesc, isPublic);
                dialog.dismiss();
            } else {
                Toast.makeText(getContext(), "Tên Playlist không được để trống", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_delete_playlist).setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeletePlaylist();
        });

        dialog.show();
    }

    private void updatePlaylistAPI(String newName, String desc, boolean isPublic) {
        String oldName = playlistName;
        String oldDesc = playlistDescription;

        playlistName = newName;
        playlistDescription = desc;
        tvName.setText(newName);
        tvToolbarTitle.setText(newName);
        if (!desc.trim().isEmpty()) {
            tvPlaylistDescription.setText(desc);
        } else {
            tvPlaylistDescription.setText("Giai điệu mang đậm dấu ấn cá nhân của bạn.");
        }

        okhttp3.RequestBody nameBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), newName);
        okhttp3.RequestBody descBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), desc);
        okhttp3.RequestBody isPublicBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), isPublic ? "true" : "false");

        okhttp3.MultipartBody.Part imagePart = null;
        if (selectedImageUri != null) {
            java.io.File file = getFileFromUri(selectedImageUri);
            if (file != null) {
                okhttp3.RequestBody requestFile = okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/*"), file);
                imagePart = okhttp3.MultipartBody.Part.createFormData("cover_image", file.getName(), requestFile);
            }
        }

        RetrofitClient.getApiService().updatePlaylist(playlistId, nameBody, descBody, isPublicBody, imagePart).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull retrofit2.Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    playlistName = oldName;
                    tvName.setText(oldName);
                    tvToolbarTitle.setText(oldName);
                    Toast.makeText(getContext(), "Lỗi Server, đã hoàn tác tên", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Đã cập nhật Playlist thành công!", Toast.LENGTH_SHORT).show();

                    JsonObject body = response.body();
                    if (body != null && body.has("new_cover_url") && !body.get("new_cover_url").isJsonNull()) {
                        playlistCover = body.get("new_cover_url").getAsString();
                    }

                    if (selectedImageUri != null) {
                        imgCover.setImageURI(selectedImageUri);
                        imgCover.setVisibility(View.VISIBLE);
                        if (layoutCollage != null) layoutCollage.setVisibility(View.GONE);
                        isCustomCover = true;
                    }

                    Intent updateIntent = new Intent("com.example.musicforlife.ACTION_PLAYLIST_UPDATED");
                    updateIntent.setPackage(requireContext().getPackageName());
                    updateIntent.putExtra("playlist_id", playlistId);
                    updateIntent.putExtra("updated_at", System.currentTimeMillis());
                    updateIntent.putExtra("new_name", newName);
                    if (playlistCover != null) {
                        updateIntent.putExtra("new_cover_url", playlistCover);
                    }
                    requireContext().sendBroadcast(updateIntent);
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                playlistName = oldName;
                tvName.setText(oldName);
                tvToolbarTitle.setText(oldName);
                Toast.makeText(getContext(), "Lỗi mạng", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeletePlaylist() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Cảnh báo")
                .setMessage("Xóa Playlist này không? Không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> deletePlaylistAPI())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deletePlaylistAPI() {
        RetrofitClient.getApiService().deletePlaylist(playlistId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(getContext(), "Đã xóa Playlist!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent("com.example.musicforlife.ACTION_PLAYLIST_DELETED");
                    intent.setPackage(requireContext().getPackageName());
                    intent.putExtra("playlist_id", playlistId);
                    requireContext().sendBroadcast(intent);

                    requireActivity().getSupportFragmentManager().popBackStack();
                } else {
                    Toast.makeText(getContext(), "Lỗi khi xóa Playlist!", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Toast.makeText(getContext(), "Lỗi kết nối Server!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSwipeAndDrag() {
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT
        ) {
            boolean isOrderChanged = false;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();

                Collections.swap(currentSongsList, fromPosition, toPosition);
                if (songAdapter != null) {
                    songAdapter.notifyItemMoved(fromPosition, toPosition);
                }

                if (layoutSearchBarDetail != null && layoutSearchBarDetail.getVisibility() == View.GONE) {
                    Collections.swap(fullSongsList, fromPosition, toPosition);
                }

                isOrderChanged = true;
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                if (isOrderChanged) {
                    if (layoutSearchBarDetail == null || layoutSearchBarDetail.getVisibility() == View.GONE) {
                        saveNewOrderToServer();
                    }
                    isOrderChanged = false;
                }
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                int position = viewHolder.getAdapterPosition();
                if (position >= 0 && position < currentSongsList.size()) {
                    Song songToRemove = currentSongsList.get(position);

                    currentSongsList.remove(position);
                    if (songAdapter != null) {
                        songAdapter.notifyItemRemoved(position);
                        songAdapter.notifyItemRangeChanged(position, currentSongsList.size());
                    }

                    for (int i = 0; i < fullSongsList.size(); i++) {
                        if (fullSongsList.get(i).getId() == songToRemove.getId()) {
                            fullSongsList.remove(i);
                            break;
                        }
                    }

                    updatePlaylistCoverCollage(fullSongsList);

                    if (isFavoritePlaylist) {
                        SongAdapter.globalLikedSongsCache.remove(songToRemove.getId());

                        Intent favIntent = new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
                        favIntent.setPackage(requireContext().getPackageName());
                        favIntent.putExtra("EXTRA_SONG", songToRemove);
                        favIntent.putExtra("EXTRA_IS_LIKED", false);
                        requireContext().sendBroadcast(favIntent);

                        removeSongFromFavoritesAPI(songToRemove, position);
                    } else {
                        removeSongFromCustomPlaylistAPI(songToRemove, position);
                    }
                }
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(rvSongs);
    }

    private void saveNewOrderToServer() {
        if (isFavoritePlaylist || playlistId == -1) return;

        List<Integer> newOrderIds = new ArrayList<>();
        for (Song s : fullSongsList) {
            newOrderIds.add(s.getId());
        }

        HashMap<String, Object> body = new HashMap<>();
        body.put("song_ids", newOrderIds);

        RetrofitClient.getApiService().updatePlaylistOrder(playlistId, body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(getContext(), "Lỗi khi lưu thứ tự mới!", Toast.LENGTH_SHORT).show();
                } else {
                    Log.d("DragDrop", "Đã lưu thứ tự mới lên server thành công");
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Toast.makeText(getContext(), "Lỗi mạng, không thể lưu thứ tự!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void removeSongFromCustomPlaylistAPI(Song song, int originalPosition) {
        RetrofitClient.getApiService().removeSongFromPlaylist(playlistId, song.getId()).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) revertSwipe(song, originalPosition);
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                revertSwipe(song, originalPosition);
            }
        });
    }

    private void removeSongFromFavoritesAPI(Song song, int originalPosition) {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        HashMap<String, Object> body = new HashMap<>();
        body.put("username", prefs.getString("userUsername", ""));
        body.put("song_id", song.getId());

        RetrofitClient.getApiService().toggleFavorite(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    SongAdapter.globalLikedSongsCache.add(song.getId());
                    revertSwipe(song, originalPosition);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                SongAdapter.globalLikedSongsCache.add(song.getId());
                revertSwipe(song, originalPosition);
            }
        });
    }

    private void revertSwipe(Song song, int position) {
        currentSongsList.add(position, song);
        if (songAdapter != null) {
            songAdapter.notifyItemInserted(position);
            songAdapter.notifyItemRangeChanged(position, currentSongsList.size());
        }

        boolean existsInFull = false;
        for (Song s : fullSongsList) {
            if (s.getId() == song.getId()) {
                existsInFull = true;
                break;
            }
        }
        if (!existsInFull) {
            fullSongsList.add(song);
        }

        updatePlaylistCoverCollage(fullSongsList);

        if (isFavoritePlaylist) {
            Intent favIntent = new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
            favIntent.setPackage(requireContext().getPackageName());
            favIntent.putExtra("EXTRA_SONG", song);
            favIntent.putExtra("EXTRA_IS_LIKED", true);
            requireContext().sendBroadcast(favIntent);
        }
        Toast.makeText(getContext(), "Lỗi kết nối Server, đã hoàn tác", Toast.LENGTH_SHORT).show();
    }

    private void fetchSongsInPlaylist(int id) {
        if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.VISIBLE);

        Call<List<Song>> call = RetrofitClient.getApiService().getSongsInPlaylist(id);
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                if (result != null) {
                    fullSongsList.clear();
                    fullSongsList.addAll(result);

                    currentSongsList.clear();
                    currentSongsList.addAll(result);

                    if (songAdapter != null) {
                        songAdapter.updateData(currentSongsList);
                        songAdapter.notifyDataSetChanged();
                    }
                    updatePlaylistCoverCollage(fullSongsList);
                }

                if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                if (btnPlayRandom != null) btnPlayRandom.setVisibility(View.VISIBLE);

                fetchQuickAddSuggestions();
            }

            @Override
            public void onError(String errorMessage) {
                if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                if (btnPlayRandom != null) btnPlayRandom.setVisibility(View.GONE);
            }
        });
    }

    private void fetchFavoriteSongs(String username) {
        if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.VISIBLE);

        Call<List<Song>> call = RetrofitClient.getApiService().getFavoriteSongs(username);
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                if (result != null) {
                    List<Song> newSongs = new ArrayList<>();
                    for (Song s : result) {
                        if (SongAdapter.globalLikedSongsCache.contains(s.getId())) newSongs.add(s);
                    }

                    fullSongsList.clear();
                    fullSongsList.addAll(newSongs);

                    currentSongsList.clear();
                    currentSongsList.addAll(newSongs);

                    if (songAdapter != null) {
                        songAdapter.updateData(currentSongsList);
                        songAdapter.notifyDataSetChanged();
                    }
                    updatePlaylistCoverCollage(fullSongsList);
                }

                if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                if (btnPlayRandom != null) btnPlayRandom.setVisibility(View.VISIBLE);

                fetchQuickAddSuggestions();
            }

            @Override
            public void onError(String errorMessage) {
                if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                if (btnPlayRandom != null) btnPlayRandom.setVisibility(View.GONE);
            }
        });
    }

    private void fetchQuickAddSuggestions() {
        if (isFetchingQuickAdd) return;
        isFetchingQuickAdd = true;

        Call<List<Song>> call = RetrofitClient.getApiService().getRecommendations(quickAddPage, 15);
        call.enqueue(new Callback<List<Song>>() {
            @Override
            public void onResponse(@NonNull Call<List<Song>> call, @NonNull Response<List<Song>> response) {
                isFetchingQuickAdd = false;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {

                    java.util.Set<Integer> existingIds = new java.util.HashSet<>();
                    for (Song s : fullSongsList) {
                        existingIds.add(s.getId());
                    }
                    if (quickAddAdapter != null) {
                        for (Song s : quickAddAdapter.getSuggestionList()) {
                            existingIds.add(s.getId());
                        }
                    }

                    List<Song> suggestions = new ArrayList<>();
                    for (Song s : response.body()) {
                        if (!existingIds.contains(s.getId())) {
                            suggestions.add(s);
                            existingIds.add(s.getId());
                        }
                    }

                    if (!suggestions.isEmpty() && layoutQuickAddSection != null && quickAddAdapter != null) {
                        layoutQuickAddSection.setVisibility(View.VISIBLE);

                        if (quickAddPage == 1) {
                            quickAddAdapter.updateData(suggestions);
                        } else {
                            quickAddAdapter.appendData(suggestions);
                        }

                        quickAddPage++;

                        if (MusicService.globalCurrentSong != null) {
                            quickAddAdapter.updatePlayingState(MusicService.globalCurrentSong.getId());
                        }
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<List<Song>> call, @NonNull Throwable t) {
                isFetchingQuickAdd = false;
            }
        });
    }

    private void addSongToPlaylist(Song song, int suggestionPosition) {
        fullSongsList.add(song);

        if (layoutSearchBarDetail == null || layoutSearchBarDetail.getVisibility() == View.GONE) {
            currentSongsList.add(song);
            if (songAdapter != null) songAdapter.notifyItemInserted(currentSongsList.size() - 1);
        } else {
            filterSongs(etSearchSong.getText().toString());
        }

        if (quickAddAdapter != null) {
            quickAddAdapter.removeItem(suggestionPosition);
            if (quickAddAdapter.getItemCount() <= 3) {
                fetchQuickAddSuggestions();
            }
        }

        updatePlaylistCoverCollage(fullSongsList);
        Toast.makeText(getContext(), "✨ Đã thêm " + song.getTitle(), Toast.LENGTH_SHORT).show();

        if (isFavoritePlaylist) {
            SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            HashMap<String, Object> body = new HashMap<>();
            body.put("username", prefs.getString("userUsername", ""));
            body.put("song_id", song.getId());

            SongAdapter.globalLikedSongsCache.add(song.getId());
            RetrofitClient.getApiService().toggleFavorite(body).enqueue(new Callback<JsonObject>() {
                @Override public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {}
                @Override public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {}
            });

            Intent favIntent = new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
            favIntent.setPackage(requireContext().getPackageName());
            favIntent.putExtra("EXTRA_SONG", song);
            favIntent.putExtra("EXTRA_IS_LIKED", true);
            requireContext().sendBroadcast(favIntent);
        } else {
            HashMap<String, Object> body = new HashMap<>();
            body.put("playlist_id", playlistId);
            body.put("song_id", song.getId());

            RetrofitClient.getApiService().addSongToPlaylist(body).enqueue(new Callback<JsonObject>() {
                @Override public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {}
                @Override public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {}
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        filter.addAction(MusicService.ACTION_UPDATE_UI);
        ContextCompat.registerReceiver(requireContext(), playlistReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (isFavoritePlaylist) {
            SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            String username = prefs.getString("userUsername", "");
            if (!username.isEmpty()) fetchFavoriteSongs(username);
        } else if (playlistId != -1) {
            fetchSongsInPlaylist(playlistId);
        }

        if (quickAddAdapter != null && MusicService.globalCurrentSong != null) {
            quickAddAdapter.updatePlayingState(MusicService.globalCurrentSong.getId());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(playlistReceiver);
        } catch (IllegalArgumentException e) { }
    }

    private String removeAccents(String str) {
        if (str == null) return "";
        try {
            String temp = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD);
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
            return pattern.matcher(temp).replaceAll("").replace('đ', 'd').replace('Đ', 'd').toLowerCase();
        } catch (Exception e) {
            return str.toLowerCase();
        }
    }
}