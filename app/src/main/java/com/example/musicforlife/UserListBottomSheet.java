package com.example.musicforlife;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

import retrofit2.Call;

public class UserListBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TYPE = "list_type";
    private static final String ARG_USERNAME = "username";

    private String type;
    private String username;

    private TextView tvTitle;
    private RecyclerView rvUsers;
    private ProgressBar progressBar;

    private UserAdapter userAdapter;

    public static UserListBottomSheet newInstance(String type, String username) {
        UserListBottomSheet fragment = new UserListBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        args.putString(ARG_USERNAME, username);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = getArguments().getString(ARG_TYPE);
            username = getArguments().getString(ARG_USERNAME);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_users, container, false);

        tvTitle = view.findViewById(R.id.tv_bottom_sheet_title);
        rvUsers = view.findViewById(R.id.rv_users);
        progressBar = view.findViewById(R.id.progress_bar_users);

        rvUsers.setLayoutManager(new LinearLayoutManager(getContext()));

        if (type.equals("followers")) {
            tvTitle.setText("Người theo dõi");
        } else if (type.equals("following")) {
            tvTitle.setText("Đang theo dõi");
        } else if (type.equals("artist_followers")) {
            tvTitle.setText("Người theo dõi nghệ sĩ");
        }

        loadData();

        return view;
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);

        Call<List<User>> call;

        if (type.equals("followers")) {
            call = RetrofitClient.getApiService().getUserFollowers(username);
        } else if (type.equals("following")) {
            call = RetrofitClient.getApiService().getUserFollowing(username);
        } else {
            call = RetrofitClient.getApiService().getArtistFollowers(username);
        }

        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<User>>() {
            @Override
            public void onSuccess(List<User> result) {
                if (isAdded()) {
                    progressBar.setVisibility(View.GONE);

                    if (result != null && !result.isEmpty()) {
                        userAdapter = new UserAdapter(getContext(), result, new UserAdapter.OnItemClickListener() {
                            @Override
                            public void onUserClick(User item) {
                                dismiss();

                                SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                                String myUsername = prefs.getString("userUsername", "");

                                if ("artist".equals(item.getType())) {
                                    if (getActivity() instanceof MainActivity) {
                                        ArtistFragment fragment = ArtistFragment.newInstance(item.getUsername());
                                        ((MainActivity) getActivity()).navigateToDetailFragment(fragment, "ArtistFragment");
                                    }
                                } else {
                                    if (item.getUsername().equals(myUsername)) {
                                        if (getActivity() instanceof MainActivity) {
                                            ((MainActivity) getActivity()).switchToAccountTab();
                                        }
                                    } else {
                                        Intent intent = new Intent(getContext(), OtherUserProfileActivity.class);
                                        intent.putExtra("TARGET_USERNAME", item.getUsername());
                                        startActivity(intent);
                                    }
                                }
                            }
                        });
                        rvUsers.setAdapter(userAdapter);
                    } else {
                        Toast.makeText(getContext(), "Danh sách trống!", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (isAdded()) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Lỗi tải danh sách: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}