package com.example.musicforlife;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    private ImageButton btnBack;
    private SwitchMaterial switchHighQuality;
    private LinearLayout btnClearCache;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        prefs = requireActivity().getSharedPreferences("AppSettings", Context.MODE_PRIVATE);

        btnBack = view.findViewById(R.id.btn_back_settings);
        switchHighQuality = view.findViewById(R.id.switch_high_quality);
        btnClearCache = view.findViewById(R.id.btn_clear_cache);

        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });

        boolean isHighQuality = prefs.getBoolean("high_quality", false);
        switchHighQuality.setChecked(isHighQuality);

        switchHighQuality.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("high_quality", isChecked).apply();
            if (isChecked) {
                Toast.makeText(getContext(), "Đã bật luồng âm thanh 320kbps", Toast.LENGTH_SHORT).show();
            }
        });

        btnClearCache.setOnClickListener(v -> {
            new Thread(() -> {
                Glide.get(requireContext()).clearDiskCache();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Glide.get(requireContext()).clearMemory();
                        Toast.makeText(getContext(), "Đã dọn dẹp bộ nhớ đệm thành công!", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });

        return view;
    }
}