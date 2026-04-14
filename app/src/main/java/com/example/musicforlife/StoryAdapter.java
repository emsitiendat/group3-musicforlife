package com.example.musicforlife;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StoryAdapter extends RecyclerView.Adapter<StoryAdapter.StoryViewHolder> {

    private Context context;
    private List<StoryUser> storyUsers;

    public StoryAdapter(Context context, List<StoryUser> storyUsers) {
        this.context = context;
        this.storyUsers = storyUsers;
    }

    @NonNull
    @Override
    public StoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_story, parent, false);
        return new StoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoryViewHolder holder, int position) {
        StoryUser user = storyUsers.get(position);

        holder.tvUsername.setText(user.getDisplayName());

        String avatarUrl = (user.getAvatarUrl() != null && !user.getAvatarUrl().equals("default_avatar"))
                ? user.getAvatarUrl() : "";
        GlideHelper.loadCircleForAdapter(context, avatarUrl, holder.imgAvatar);

        holder.itemView.setOnClickListener(v -> {
            Toast.makeText(context, "Mở Story của " + user.getUsername(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return storyUsers != null ? storyUsers.size() : 0;
    }

    public static class StoryViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAvatar;
        TextView tvUsername;

        public StoryViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.img_story_avatar);
            tvUsername = itemView.findViewById(R.id.tv_story_username);
        }
    }
}