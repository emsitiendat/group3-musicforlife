package com.example.musicforlife;

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
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import retrofit2.Call;

public class PlaylistsFragment extends Fragment implements ScrollToTopListener {

    private int savedScrollPosition = 0;

    private NestedScrollView scrollViewPlaylists;
    private View layoutFavoriteSongs;
    private TextView tvFavCount;
    private ImageView btnPlayFavorite;
    private ImageButton btnCreatePlaylist;

    private ImageView imgProfileAvatar;
    private TextView tvLibraryTitle;
    private LinearLayout layoutSearchBar;
    private EditText etSearchLibrary;
    private ImageButton btnCloseSearch;
    private ImageButton btnSearchLibrary;

    private TextView tvSocialPlaylistTitle;
    private RecyclerView rvSocialPlaylists;
    private PlaylistAdapter socialAdapter;

    private TextView tvBlendMixTitle;
    private RecyclerView rvBlendMix;
    private BlendMixAdapter blendAdapter;

    private RelativeLayout layoutMyPlaylistHeader;
    private TextView tvMyPlaylistTitle;
    private ImageButton btnSort, btnViewToggle;
    private RecyclerView rvMyPlaylists;
    private PlaylistAdapter playlistAdapter;

    private TextView tvMyArtistsTitle;
    private RecyclerView rvMyArtists;
    private ArtistCircleAdapter artistAdapter;

    private LinearLayout layoutGlobalLoading;
    private LinearLayout layoutEmptyState;
    private TextView btnEmptyStateCreate;

    private TextView pillAll, pillPlaylists, pillArtists;

    private int currentFilterType = 0;
    private String currentSearchQuery = "";
    private boolean isGridView = true;

    private List<Playlist> globalPlaylists = new ArrayList<>();
    private List<Song> globalArtists = new ArrayList<>();
    private List<Playlist> globalBlendMixes = new ArrayList<>();

    private android.net.Uri selectedImageUri = null;
    private ImageView currentPreviewImageView = null;

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
            java.io.File tempFile = new java.io.File(requireContext().getCacheDir(), "temp_cover.jpg");
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

    private final BroadcastReceiver updateUIReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if ("com.example.musicforlife.ACTION_FAVORITE_CHANGED".equals(action)) {
                boolean isLiked = intent.getBooleanExtra("EXTRA_IS_LIKED", false);
                if (tvFavCount != null) {
                    tvFavCount.setText(SongAdapter.globalLikedSongsCache.size() + " bài hát");
                }
            }
            else if ("com.example.musicforlife.ACTION_PLAYLIST_UPDATED".equals(action)) {
                int id = intent.getIntExtra("playlist_id", -1);
                long updatedAt = intent.getLongExtra("updated_at", 0);
                String newCoverUrl = intent.getStringExtra("new_cover_url");
                String newName = intent.getStringExtra("new_name");

                for (Playlist p : globalPlaylists) {
                    if (p.getId() == id) {
                        p.setUpdatedAt(updatedAt);
                        if (newCoverUrl != null) {
                            p.setCoverArtPath(newCoverUrl);
                            p.setCustomCover(true);
                        }
                        if (newName != null) {
                            p.setName(newName);
                        }
                        break;
                    }
                }

                if (playlistAdapter != null) {
                    filterData(currentFilterType);
                    playlistAdapter.notifyDataSetChanged();
                }
            }
            else if ("com.example.musicforlife.ACTION_PLAYLIST_DELETED".equals(action)) {
                int id = intent.getIntExtra("playlist_id", -1);
                if (id != -1) {
                    for (int i = 0; i < globalPlaylists.size(); i++) {
                        if (globalPlaylists.get(i).getId() == id) {
                            globalPlaylists.remove(i);
                            break;
                        }
                    }
                    for (int i = 0; i < globalBlendMixes.size(); i++) {
                        if (globalBlendMixes.get(i).getId() == id) {
                            globalBlendMixes.remove(i);
                            break;
                        }
                    }
                    filterData(currentFilterType);
                    if (playlistAdapter != null) {
                        playlistAdapter.notifyDataSetChanged();
                    }
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlists, container, false);

        initViews(view);
        setupRecyclerViews();
        setupClicks();
        setupSearchLogic();
        setupFilters();
        setupToggleView();
        loadUserAvatar();

        if (!globalPlaylists.isEmpty() || !globalArtists.isEmpty() || !globalBlendMixes.isEmpty()) {
            filterData(currentFilterType);
        }

        fetchSocialPlaylists();
        fetchUserPlaylists();
        fetchUserArtists();

        return view;
    }

    private void initViews(View view) {
        scrollViewPlaylists = view.findViewById(R.id.scroll_view_playlists);
        layoutFavoriteSongs = view.findViewById(R.id.layout_favorite_songs);
        tvFavCount = view.findViewById(R.id.tv_favorite_count);
        btnPlayFavorite = view.findViewById(R.id.btn_play_favorite);
        btnCreatePlaylist = view.findViewById(R.id.btn_create_playlist);

        layoutGlobalLoading = view.findViewById(R.id.layout_global_loading);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        btnEmptyStateCreate = view.findViewById(R.id.btn_empty_state_create);

        pillAll = view.findViewById(R.id.pill_all);
        pillPlaylists = view.findViewById(R.id.pill_playlists);
        pillArtists = view.findViewById(R.id.pill_artists);

        imgProfileAvatar = view.findViewById(R.id.img_profile_avatar);
        tvLibraryTitle = view.findViewById(R.id.tv_library_title);
        layoutSearchBar = view.findViewById(R.id.layout_search_bar);
        etSearchLibrary = view.findViewById(R.id.et_search_library);
        btnCloseSearch = view.findViewById(R.id.btn_close_search);
        btnSearchLibrary = view.findViewById(R.id.btn_search_library);

        tvSocialPlaylistTitle = view.findViewById(R.id.tv_social_playlist_title);
        rvSocialPlaylists = view.findViewById(R.id.rv_social_playlists);

        tvBlendMixTitle = view.findViewById(R.id.tv_blend_mix_title);
        rvBlendMix = view.findViewById(R.id.rv_blend_mix);

        layoutMyPlaylistHeader = view.findViewById(R.id.layout_my_playlist_header);
        tvMyPlaylistTitle = view.findViewById(R.id.tv_my_playlist_title);
        btnSort = view.findViewById(R.id.btn_sort);
        btnViewToggle = view.findViewById(R.id.btn_view_toggle);
        rvMyPlaylists = view.findViewById(R.id.rv_my_playlists);

        tvMyArtistsTitle = view.findViewById(R.id.tv_my_artists_title);
        rvMyArtists = view.findViewById(R.id.rv_my_artists);
    }

    private void setupRecyclerViews() {
        rvSocialPlaylists.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        socialAdapter = new PlaylistAdapter(getContext(), new ArrayList<>());
        rvSocialPlaylists.setAdapter(socialAdapter);
        rvSocialPlaylists.setFocusable(false);

        rvBlendMix.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        blendAdapter = new BlendMixAdapter(getContext(), new ArrayList<>(), playlist -> {
            PlaylistDetailFragment fragment = new PlaylistDetailFragment();
            Bundle args = new Bundle();
            args.putInt("EXTRA_PLAYLIST_ID", playlist.getId());
            args.putString("EXTRA_PLAYLIST_NAME", playlist.getName());
            args.putBoolean("EXTRA_IS_BLEND", true);

            SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            args.putString("EXTRA_OWNER_AVATAR", playlist.getOwnerAvatar() != null ? playlist.getOwnerAvatar() : "");
            args.putString("EXTRA_PARTNER_AVATAR", playlist.getPartnerAvatar() != null ? playlist.getPartnerAvatar() : "");
            args.putString("EXTRA_OWNER_USERNAME", playlist.getOwnerUsername() != null ? playlist.getOwnerUsername() : "");
            args.putString("EXTRA_PARTNER_USERNAME", playlist.getPartnerUsername() != null ? playlist.getPartnerUsername() : "");

            fragment.setArguments(args);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToDetailFragment(fragment, "PlaylistDetailFragment");
            }
        });
        rvBlendMix.setAdapter(blendAdapter);
        rvBlendMix.setFocusable(false);

        rvMyPlaylists.setLayoutManager(new GridLayoutManager(getContext(), 2));
        playlistAdapter = new PlaylistAdapter(getContext(), new ArrayList<>());
        rvMyPlaylists.setAdapter(playlistAdapter);
        rvMyPlaylists.setFocusable(false);

        rvMyArtists.setLayoutManager(new GridLayoutManager(getContext(), 3));
        artistAdapter = new ArtistCircleAdapter(getContext(), new ArrayList<>());
        rvMyArtists.setAdapter(artistAdapter);
        rvMyArtists.setFocusable(false);
    }

    private void setupClicks() {
        if (imgProfileAvatar != null) {
            imgProfileAvatar.setOnClickListener(v -> {
                if (getActivity() != null) {
                    View navAccount = requireActivity().findViewById(R.id.nav_account);
                    if (navAccount != null) {
                        navAccount.performClick();
                    }
                }
            });
        }

        if (layoutFavoriteSongs != null) {
            layoutFavoriteSongs.setOnClickListener(v -> {
                PlaylistDetailFragment fragment = new PlaylistDetailFragment();
                Bundle args = new Bundle();
                args.putBoolean("is_favorite_playlist", true);
                fragment.setArguments(args);
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToDetailFragment(fragment, "FavoritePlaylist");
                }
            });
        }

        if (btnPlayFavorite != null) {
            btnPlayFavorite.setOnClickListener(v -> {
                SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                String username = prefs.getString("userUsername", "");
                if (username.isEmpty()) {
                    Toast.makeText(getContext(), "Vui lòng đăng nhập để nghe nhạc!", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(getContext(), "Đang mở Bài hát yêu thích...", Toast.LENGTH_SHORT).show();
                Call<List<Song>> call = RetrofitClient.getApiService().getFavoriteSongs(username);
                call.enqueue(new retrofit2.Callback<List<Song>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Song>> call, @NonNull retrofit2.Response<List<Song>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            List<Song> songs = response.body();
                            int randomPosition = new java.util.Random().nextInt(songs.size());
                            Intent playIntent = new Intent(getContext(), MusicService.class);
                            playIntent.setAction(MusicService.ACTION_PLAY);
                            playIntent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(songs));
                            playIntent.putExtra("EXTRA_SONG_POSITION", randomPosition);
                            requireContext().startService(playIntent);
                        } else {
                            Toast.makeText(getContext(), "Bạn chưa có bài hát yêu thích nào!", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<List<Song>> call, @NonNull Throwable t) {
                        Toast.makeText(getContext(), "Lỗi kết nối mạng!", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        View.OnClickListener createListener = v -> showCreatePlaylistDialog();
        if (btnCreatePlaylist != null) btnCreatePlaylist.setOnClickListener(createListener);
        if (btnEmptyStateCreate != null) btnEmptyStateCreate.setOnClickListener(createListener);
    }

    private void setupSearchLogic() {
        if (btnSearchLibrary != null) {
            btnSearchLibrary.setOnClickListener(v -> {
                tvLibraryTitle.setVisibility(View.GONE);
                btnSearchLibrary.setVisibility(View.GONE);
                layoutSearchBar.setVisibility(View.VISIBLE);
                etSearchLibrary.requestFocus();
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etSearchLibrary, InputMethodManager.SHOW_IMPLICIT);
            });
        }

        if (btnCloseSearch != null) {
            btnCloseSearch.setOnClickListener(v -> {
                etSearchLibrary.setText("");
                currentSearchQuery = "";
                layoutSearchBar.setVisibility(View.GONE);
                tvLibraryTitle.setVisibility(View.VISIBLE);
                btnSearchLibrary.setVisibility(View.VISIBLE);
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(etSearchLibrary.getWindowToken(), 0);
                filterData(currentFilterType);
            });
        }

        if (etSearchLibrary != null) {
            etSearchLibrary.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    currentSearchQuery = s.toString().trim().toLowerCase();
                    filterData(currentFilterType);
                }
            });
        }
    }

    private void setupToggleView() {
        if (btnViewToggle == null || btnSort == null) return;
        btnViewToggle.setOnClickListener(v -> {
            isGridView = !isGridView;
            if (isGridView) {
                rvMyPlaylists.setLayoutManager(new GridLayoutManager(getContext(), 2));
                btnViewToggle.setImageResource(R.drawable.ic_view_list);
            } else {
                rvMyPlaylists.setLayoutManager(new LinearLayoutManager(getContext()));
                btnViewToggle.setImageResource(R.drawable.ic_grid);
            }
            if (playlistAdapter != null) rvMyPlaylists.setAdapter(playlistAdapter);
        });

        btnSort.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(getContext(), btnSort);
            popupMenu.getMenu().add("Tên (A-Z)");
            popupMenu.getMenu().add("Tên (Z-A)");
            popupMenu.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if (title.equals("Tên (A-Z)")) sortData(true);
                else if (title.equals("Tên (Z-A)")) sortData(false);
                return true;
            });
            popupMenu.show();
        });
    }

    private void sortData(boolean isAscending) {
        Collections.sort(globalPlaylists, (p1, p2) -> {
            String name1 = p1.getName() != null ? p1.getName() : "";
            String name2 = p2.getName() != null ? p2.getName() : "";
            return isAscending ? name1.compareToIgnoreCase(name2) : name2.compareToIgnoreCase(name1);
        });

        Collections.sort(globalArtists, (a1, a2) -> {
            String name1 = a1.getArtist() != null ? a1.getArtist() : "";
            String name2 = a2.getArtist() != null ? a2.getArtist() : "";
            return isAscending ? name1.compareToIgnoreCase(name2) : name2.compareToIgnoreCase(name1);
        });

        filterData(currentFilterType);
        Toast.makeText(getContext(), isAscending ? "Đã sắp xếp A-Z" : "Đã sắp xếp Z-A", Toast.LENGTH_SHORT).show();
    }

    private void loadUserAvatar() {
        if (imgProfileAvatar == null || getActivity() == null) return;
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String avatarUrl = prefs.getString("userAvatar", "");
        if (avatarUrl.isEmpty()) {
            imgProfileAvatar.setImageResource(R.drawable.default_avatar);
        } else {
            GlideHelper.loadAvatar(this, avatarUrl, imgProfileAvatar);
        }
    }

    private void setupFilters() {
        updatePillUI(pillAll);
        pillAll.setOnClickListener(v -> { updatePillUI(pillAll); filterData(0); });
        pillPlaylists.setOnClickListener(v -> { updatePillUI(pillPlaylists); filterData(1); });
        pillArtists.setOnClickListener(v -> { updatePillUI(pillArtists); filterData(2); });
    }

    private void updatePillUI(TextView selectedPill) {
        if (getContext() == null) return;
        TextView[] allPills = {pillAll, pillPlaylists, pillArtists};
        for (TextView pill : allPills) {
            pill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1AFFFFFF")));
            pill.setTextColor(Color.parseColor("#E0E0E0"));
            pill.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        int accentColor = ContextCompat.getColor(getContext(), R.color.color_accent);
        selectedPill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        selectedPill.setTextColor(Color.WHITE);
        selectedPill.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void filterData(int filterType) {
        currentFilterType = filterType;

        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

        List<Playlist> searchedPlaylists = new ArrayList<>();
        List<Song> searchedArtists = new ArrayList<>();
        List<Playlist> searchedBlends = new ArrayList<>();

        if (currentSearchQuery.isEmpty()) {
            searchedPlaylists.addAll(globalPlaylists);
            searchedArtists.addAll(globalArtists);
            searchedBlends.addAll(globalBlendMixes);
        } else {
            for (Playlist p : globalPlaylists) {
                if (p.getName() != null && p.getName().toLowerCase().contains(currentSearchQuery)) {
                    searchedPlaylists.add(p);
                }
            }
            for (Song a : globalArtists) {
                if (a.getArtist() != null && a.getArtist().toLowerCase().contains(currentSearchQuery)) {
                    searchedArtists.add(a);
                }
            }
            for (Playlist p : globalBlendMixes) {
                if (p.getName() != null && p.getName().toLowerCase().contains(currentSearchQuery)) {
                    searchedBlends.add(p);
                }
            }
        }

        playlistAdapter.updateData(searchedPlaylists);
        artistAdapter.updateData(searchedArtists);
        blendAdapter.updateData(searchedBlends);

        boolean showFav = isLoggedIn && (filterType != 2) && currentSearchQuery.isEmpty();
        layoutFavoriteSongs.setVisibility(showFav ? View.VISIBLE : View.GONE);

        boolean showSocial = (filterType == 0) && currentSearchQuery.isEmpty() && !globalPlaylists.isEmpty();
        tvSocialPlaylistTitle.setVisibility(showSocial ? View.VISIBLE : View.GONE);
        rvSocialPlaylists.setVisibility(showSocial ? View.VISIBLE : View.GONE);

        boolean showBlend = isLoggedIn && (filterType != 2) && !searchedBlends.isEmpty();
        tvBlendMixTitle.setVisibility(showBlend ? View.VISIBLE : View.GONE);
        rvBlendMix.setVisibility(showBlend ? View.VISIBLE : View.GONE);

        boolean showMyPlaylists = isLoggedIn && (filterType != 2) && !searchedPlaylists.isEmpty();
        layoutMyPlaylistHeader.setVisibility(showMyPlaylists ? View.VISIBLE : View.GONE);
        rvMyPlaylists.setVisibility(showMyPlaylists ? View.VISIBLE : View.GONE);

        boolean showArtists = isLoggedIn && (filterType != 1) && !searchedArtists.isEmpty();
        tvMyArtistsTitle.setVisibility(showArtists ? View.VISIBLE : View.GONE);
        rvMyArtists.setVisibility(showArtists ? View.VISIBLE : View.GONE);

        if (!isLoggedIn) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            ((TextView) layoutEmptyState.getChildAt(0)).setText("Tham gia cùng Music For Life");
            ((TextView) layoutEmptyState.getChildAt(1)).setText("Đăng nhập để xem thư viện và playlist của bạn.");

            btnEmptyStateCreate.setText("Đăng nhập ngay");
            btnEmptyStateCreate.setOnClickListener(v -> startActivity(new Intent(getActivity(), AuthActivity.class)));
        } else {
            boolean showEmptyState = false;
            String emptyTitle = "";
            String emptyDesc = "";

            if (filterType == 2 && searchedArtists.isEmpty()) {
                showEmptyState = true;
                emptyTitle = currentSearchQuery.isEmpty() ? "Chưa có nghệ sĩ nào" : "Không tìm thấy nghệ sĩ";
                emptyDesc = currentSearchQuery.isEmpty() ? "Hãy thêm bài hát vào playlist để theo dõi nhé." : "";
            } else if (filterType == 1 && searchedPlaylists.isEmpty() && searchedBlends.isEmpty()) {
                showEmptyState = true;
                emptyTitle = currentSearchQuery.isEmpty() ? "Chưa có Playlist nào" : "Không tìm thấy Playlist/Blend";
            } else if (filterType == 0 && searchedPlaylists.isEmpty() && searchedArtists.isEmpty() && searchedBlends.isEmpty()) {
                showEmptyState = true;
                emptyTitle = currentSearchQuery.isEmpty() ? "Thư viện trống" : "Không tìm thấy kết quả";
            }

            layoutEmptyState.setVisibility(showEmptyState ? View.VISIBLE : View.GONE);
            if (showEmptyState) {
                ((TextView) layoutEmptyState.getChildAt(0)).setText(emptyTitle);
                ((TextView) layoutEmptyState.getChildAt(1)).setText(emptyDesc);

                btnEmptyStateCreate.setText("Tạo Playlist");
                btnEmptyStateCreate.setOnClickListener(v -> showCreatePlaylistDialog());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserAvatar();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        filter.addAction("com.example.musicforlife.ACTION_PLAYLIST_UPDATED");
        filter.addAction("com.example.musicforlife.ACTION_PLAYLIST_DELETED");
        ContextCompat.registerReceiver(requireContext(), updateUIReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        filterData(currentFilterType);
        updateFavoriteCountUI();
        fetchUserPlaylists();

        if (scrollViewPlaylists != null) {
            scrollViewPlaylists.postDelayed(() -> {
                scrollViewPlaylists.scrollTo(0, savedScrollPosition);
            }, 100);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (scrollViewPlaylists != null) {
            savedScrollPosition = scrollViewPlaylists.getScrollY();
        }

        try { requireContext().unregisterReceiver(updateUIReceiver); } catch (IllegalArgumentException e) { }
    }

    private void updateFavoriteCountUI() {
        if (getContext() == null) return;

        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");
        if (username.isEmpty()) return;

        if (tvFavCount != null) {
            tvFavCount.setText(SongAdapter.globalLikedSongsCache.size() + " bài hát");
        }

        Call<List<Song>> call = RetrofitClient.getApiService().getFavoriteSongs(username);
        call.enqueue(new retrofit2.Callback<List<Song>>() {
            @Override
            public void onResponse(@NonNull Call<List<Song>> call, @NonNull retrofit2.Response<List<Song>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Song> favoriteSongs = response.body();

                    if (tvFavCount != null) {
                        tvFavCount.setText(favoriteSongs.size() + " bài hát");
                    }

                    SongAdapter.globalLikedSongsCache.clear();
                    for (Song song : favoriteSongs) {
                        SongAdapter.globalLikedSongsCache.add(song.getId());
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Song>> call, @NonNull Throwable t) {
                Log.e("PlaylistsFragment", "Lỗi lấy số lượng tim: " + t.getMessage());
            }
        });
    }

    private void fetchSocialPlaylists() {
        Call<List<Playlist>> call = RetrofitClient.getApiService().getFeaturedPlaylists();
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> result) {
                if (result != null && !result.isEmpty()) {
                    socialAdapter.updateData(result);
                    if (currentFilterType == 0 && currentSearchQuery.isEmpty()) {
                        tvSocialPlaylistTitle.setVisibility(View.VISIBLE);
                        rvSocialPlaylists.setVisibility(View.VISIBLE);
                    }
                }
            }
            @Override public void onError(String errorMessage) {}
        });
    }

    private void fetchUserPlaylists() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");
        if (username.isEmpty()) return;

        Call<List<Playlist>> call = RetrofitClient.getApiService().getUserPlaylists(username);

        ApiHelper.request(call, globalPlaylists.isEmpty() ? layoutGlobalLoading : null, null, new ApiHelper.CallbackResult<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> result) {
                if (result == null) return;

                globalPlaylists.clear();
                globalBlendMixes.clear();

                for (Playlist p : result) {
                    if (p.isBlend()) {
                        globalBlendMixes.add(p);
                    } else {
                        globalPlaylists.add(p);
                    }
                }

                filterData(currentFilterType);
            }

            @Override public void onError(String errorMessage) {}
        });
    }

    private void fetchUserArtists() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");
        if (username.isEmpty()) return;

        Call<List<Song>> call = RetrofitClient.getApiService().getUserArtists(username);
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                globalArtists.clear();
                if (result != null) globalArtists.addAll(result);
                filterData(currentFilterType);
            }
            @Override public void onError(String errorMessage) {}
        });
    }

    private void showCreatePlaylistDialog() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");
        if (username.isEmpty()) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_create_playlist, null);
        bottomSheetDialog.setContentView(view);

        selectedImageUri = null;
        currentPreviewImageView = view.findViewById(R.id.img_playlist_cover_preview);

        EditText etName = view.findViewById(R.id.et_playlist_name);
        EditText etDesc = view.findViewById(R.id.et_playlist_description);
        com.google.android.material.switchmaterial.SwitchMaterial switchPublic = view.findViewById(R.id.switch_public);
        TextView btnConfirm = view.findViewById(R.id.btn_confirm_create);

        view.findViewById(R.id.btn_cancel_create).setOnClickListener(v -> bottomSheetDialog.dismiss());

        View btnCreateBlend = view.findViewById(R.id.btn_create_blend_mix);
        if (btnCreateBlend != null) {
            btnCreateBlend.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                showCreateBlendDialog(username);
            });
        }

        View layoutCover = currentPreviewImageView.getParent() != null ? (View) currentPreviewImageView.getParent() : null;
        if(layoutCover != null) {
            layoutCover.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                imagePickerLauncher.launch(intent);
            });
        }

        androidx.cardview.widget.CardView coverPink = view.findViewById(R.id.cover_color_pink);
        androidx.cardview.widget.CardView coverRed = view.findViewById(R.id.cover_color_red);
        androidx.cardview.widget.CardView coverBlue = view.findViewById(R.id.cover_color_blue);
        androidx.cardview.widget.CardView emojiFire = view.findViewById(R.id.cover_emoji_fire);
        androidx.cardview.widget.CardView emojiHeadphone = view.findViewById(R.id.cover_emoji_headphone);

        View.OnClickListener colorClickListener = v -> {
            selectedImageUri = null;
            androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) v;
            int selectedColor = card.getCardBackgroundColor().getDefaultColor();
            currentPreviewImageView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selectedColor));
            currentPreviewImageView.setImageResource(R.drawable.ic_music);
            currentPreviewImageView.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        };

        View.OnClickListener emojiClickListener = v -> {
            selectedImageUri = null;
            currentPreviewImageView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1AFFFFFF")));
            currentPreviewImageView.setImageDrawable(null);
        };

        coverPink.setOnClickListener(colorClickListener);
        coverRed.setOnClickListener(colorClickListener);
        coverBlue.setOnClickListener(colorClickListener);
        emojiFire.setOnClickListener(emojiClickListener);
        emojiHeadphone.setOnClickListener(emojiClickListener);

        androidx.cardview.widget.CardView tagChill = view.findViewById(R.id.tag_chill);
        androidx.cardview.widget.CardView tagFocus = view.findViewById(R.id.tag_focus);
        androidx.cardview.widget.CardView tagSad = view.findViewById(R.id.tag_sad);

        View.OnClickListener tagClickListener = v -> {
            androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) v;
            TextView innerText = (TextView) card.getChildAt(0);
            String fullText = innerText.getText().toString();
            String tagText = fullText.contains(" ") ? fullText.substring(fullText.indexOf(" ") + 1) : fullText;

            etName.setText(tagText);
            etName.setSelection(tagText.length());
        };

        tagChill.setOnClickListener(tagClickListener);
        tagFocus.setOnClickListener(tagClickListener);
        tagSad.setOnClickListener(tagClickListener);

        view.findViewById(R.id.btn_ai_surprise).setOnClickListener(v -> {
            String[] aiNames = {"Vibes cực gắt", "Giai điệu chữa lành", "Code đến sáng", "Suy ngẫm đêm khuya"};
            int randomIdx = new java.util.Random().nextInt(aiNames.length);
            String aiName = aiNames[randomIdx];
            etName.setText(aiName);
            etName.setSelection(aiName.length());
            coverBlue.performClick();
            Toast.makeText(getContext(), "AI đã gợi ý một cái tên!", Toast.LENGTH_SHORT).show();
        });

        btnConfirm.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();
            boolean isPublic = switchPublic.isChecked();

            if (!name.isEmpty()) {
                createPlaylistAPI(username, name, desc, isPublic);
                bottomSheetDialog.dismiss();
            } else {
                Toast.makeText(getContext(), "Vui lòng nhập tên Playlist!", Toast.LENGTH_SHORT).show();
            }
        });

        bottomSheetDialog.setOnShowListener(dialog -> {
            etName.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etName, InputMethodManager.SHOW_IMPLICIT);
        });
        bottomSheetDialog.show();
    }

    private void createPlaylistAPI(String username, String name, String desc, boolean isPublic) {
        okhttp3.RequestBody usernameBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), username);
        okhttp3.RequestBody nameBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), name);
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

        Call<JsonObject> call = RetrofitClient.getApiService().createPlaylist(usernameBody, nameBody, descBody, isPublicBody, imagePart);
        ApiHelper.request(call, layoutGlobalLoading, null, new ApiHelper.CallbackResult<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                Toast.makeText(getContext(), "Đã tạo thành công!", Toast.LENGTH_SHORT).show();

                int newId = -1;
                String newName = name;

                if (result.has("playlist_id")) {
                    newId = result.get("playlist_id").getAsInt();
                }

                if (newId != -1) {
                    PlaylistDetailFragment fragment = new PlaylistDetailFragment();
                    Bundle args = new Bundle();
                    args.putInt("EXTRA_PLAYLIST_ID", newId);
                    args.putString("EXTRA_PLAYLIST_NAME", newName);
                    fragment.setArguments(args);
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).navigateToDetailFragment(fragment, "PlaylistDetailFragment");
                    }
                }

                fetchUserPlaylists();
            }
            @Override public void onError(String errorMessage) {
                Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCreateBlendDialog(String myUsername) {
        if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.VISIBLE);

        RetrofitClient.getApiService().getAllUsers().enqueue(new retrofit2.Callback<com.google.gson.JsonArray>() {
            @Override
            public void onResponse(@NonNull Call<com.google.gson.JsonArray> call, @NonNull retrofit2.Response<com.google.gson.JsonArray> response) {
                if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    com.google.gson.JsonArray usersArray = response.body();
                    List<String> displayNames = new ArrayList<>();
                    List<String> usernames = new ArrayList<>();

                    for (int i = 0; i < usersArray.size(); i++) {
                        JsonObject u = usersArray.get(i).getAsJsonObject();
                        String uName = u.get("username").getAsString();

                        if (uName.equals(myUsername)) continue;

                        String dName = u.has("display_name") && !u.get("display_name").isJsonNull()
                                ? u.get("display_name").getAsString() : uName;

                        displayNames.add(dName + " (@" + uName + ")");
                        usernames.add(uName);
                    }

                    if (usernames.isEmpty()) {
                        Toast.makeText(getContext(), "Chưa có người dùng nào khác để kết hợp!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String[] items = displayNames.toArray(new String[0]);
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
                    builder.setTitle("Chọn bạn bè kết hợp âm nhạc");

                    builder.setItems(items, (dialog, which) -> {
                        String selectedUsername = usernames.get(which);
                        createBlendMixAPI(myUsername, selectedUsername);
                    });

                    builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());
                    builder.show();

                } else {
                    Toast.makeText(getContext(), "Lỗi khi tải danh sách người dùng", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<com.google.gson.JsonArray> call, @NonNull Throwable t) {
                if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Lỗi kết nối mạng", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void createBlendMixAPI(String myUsername, String partnerUsername) {
        if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.VISIBLE);

        HashMap<String, Object> body = new HashMap<>();
        body.put("username", myUsername);
        body.put("partner_username", partnerUsername);

        RetrofitClient.getApiService().createBlendMix(body).enqueue(new retrofit2.Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull retrofit2.Response<JsonObject> response) {
                if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    JsonObject resBody = response.body();
                    if (resBody.has("success") && resBody.get("success").getAsBoolean()) {
                        Toast.makeText(getContext(), "Tạo Blend Mix thành công! Đang tải lại...", Toast.LENGTH_SHORT).show();
                        fetchUserPlaylists();
                    } else {
                        String msg = resBody.has("message") ? resBody.get("message").getAsString() : "Lỗi không xác định";
                        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Không tìm thấy người dùng này hoặc lỗi Server!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Lỗi kết nối mạng", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void scrollToTop() {
        if (getView() != null) {
            com.google.android.material.appbar.AppBarLayout appBarLayout = getView().findViewById(R.id.appbar_playlists);
            if (appBarLayout != null) appBarLayout.setExpanded(true, true);
        }
        if (scrollViewPlaylists != null) scrollViewPlaylists.smoothScrollTo(0, 0);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadUserAvatar();
            filterData(currentFilterType);
            updateFavoriteCountUI();
        }
    }
}