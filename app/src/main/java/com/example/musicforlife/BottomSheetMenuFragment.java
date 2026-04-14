package com.example.musicforlife;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class BottomSheetMenuFragment extends BottomSheetDialogFragment {

    private BottomSheetListener mListener;
    private float currentSpeed = 1.0f;
    private final List<TextView> speedButtons = new ArrayList<>();

    public interface BottomSheetListener {
        void onOptionClick(int optionId);
        void onSpeedChanged(float speed);
    }

    public static BottomSheetMenuFragment newInstance(float speed) {
        BottomSheetMenuFragment fragment = new BottomSheetMenuFragment();
        Bundle args = new Bundle();
        args.putFloat("CURRENT_SPEED", speed);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentSpeed = getArguments().getFloat("CURRENT_SPEED", 1.0f);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bottom_sheet_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupClick(view.findViewById(R.id.item_playlist), 1);
        setupClick(view.findViewById(R.id.item_view_artist), 5);
        setupClick(view.findViewById(R.id.item_share_community), 7);
        setupClick(view.findViewById(R.id.item_share_story), 6);
        setupClick(view.findViewById(R.id.item_timer), 3);
        setupClick(view.findViewById(R.id.item_zen_mode), 4);

        setupSpeedLayout(view.findViewById(R.id.layout_quick_speeds));
    }

    private void setupClick(View v, int optionId) {
        if (v != null) {
            v.setOnClickListener(view -> {
                if (mListener != null) mListener.onOptionClick(optionId);
                dismiss();
            });
        }
    }

    private void setupSpeedLayout(LinearLayout layout) {
        if (layout == null) return;
        layout.removeAllViews();
        speedButtons.clear();

        float[] speeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

        for (float speed : speeds) {
            TextView btn = new TextView(getContext());
            btn.setText(speed % 1 == 0 ? String.format("%.0fx", speed) : speed + "x");
            btn.setPadding(40, 20, 40, 20);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 16, 0);
            btn.setLayoutParams(lp);

            updateSpeedBtnUI(btn, speed == currentSpeed);

            btn.setOnClickListener(v -> {
                currentSpeed = speed;
                for (TextView b : speedButtons) updateSpeedBtnUI(b, false);
                updateSpeedBtnUI(btn, true);
                if (mListener != null) mListener.onSpeedChanged(speed);
            });

            speedButtons.add(btn);
            layout.addView(btn);
        }
    }

    private void updateSpeedBtnUI(TextView btn, boolean isSelected) {
        btn.setBackgroundResource(R.drawable.bg_button_surface);
        if (isSelected) {
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            btn.setTextColor(Color.BLACK);
        } else {
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1AFFFFFF")));
            btn.setTextColor(Color.WHITE);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try { mListener = (BottomSheetListener) context; }
        catch (ClassCastException e) { throw new ClassCastException(context + " must implement BottomSheetListener"); }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
}