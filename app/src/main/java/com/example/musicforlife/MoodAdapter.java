package com.example.musicforlife;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MoodAdapter extends RecyclerView.Adapter<MoodAdapter.MoodViewHolder> {
    private Context context;
    private List<Mood> moodList;

    public interface OnMoodClickListener {
        void onMoodClick(Mood mood);
    }
    private OnMoodClickListener listener;

    public MoodAdapter(Context context, List<Mood> moodList, OnMoodClickListener listener) {
        this.context = context;
        this.moodList = moodList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_mood_card, parent, false);
        return new MoodViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MoodViewHolder holder, int position) {
        Mood mood = moodList.get(position);
        holder.tvTitle.setText(mood.getTitle());
        holder.cardMood.setCardBackgroundColor(Color.parseColor(mood.getColorHex()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMoodClick(mood);
            }
        });
    }

    @Override
    public int getItemCount() { return moodList.size(); }

    public static class MoodViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        CardView cardMood;
        public MoodViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_mood_title);
            cardMood = itemView.findViewById(R.id.card_mood);
        }
    }
}