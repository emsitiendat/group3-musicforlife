package com.example.musicforlife;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.ArrayList;
import java.util.List;

public class SocialListeningAdapter extends RecyclerView.Adapter<SocialListeningAdapter.SocialViewHolder> {

    private Context context;
    private List<FriendActivity> friendList;

    public SocialListeningAdapter(Context context, List<FriendActivity> friendList) {
        this.context = context;
        this.friendList = friendList;
    }

    @NonNull
    @Override
    public SocialViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_social_listening, parent, false);
        return new SocialViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SocialViewHolder holder, int position) {
        FriendActivity friendActivity = friendList.get(position);
        Song currentSong = friendActivity.getCurrentSong();

        holder.tvFriendName.setText(friendActivity.getFriendName());

        if (currentSong != null) {
            holder.tvSongTitle.setText(currentSong.getTitle());

            GlideHelper.loadRoundedForAdapter(context, currentSong.getCoverArtPath(), holder.imgSongCover, 8);

            holder.itemView.setOnClickListener(v -> {
                List<Song> songList = new ArrayList<>();
                songList.add(currentSong);

                Intent intent = new Intent(context, MusicService.class);
                intent.setAction(MusicService.ACTION_PLAY);
                intent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(songList));
                intent.putExtra("EXTRA_SONG_POSITION", 0);
                context.startService(intent);
            });
        } else {
            holder.tvSongTitle.setText("Đang không nghe gì");
            GlideHelper.loadRoundedForAdapter(context, "", holder.imgSongCover, 8);
        }

        String avatarUrl = (friendActivity.getAvatarUrl() != null) ? friendActivity.getAvatarUrl() : "";
        GlideHelper.loadCircleForAdapter(context, avatarUrl, holder.imgFriendAvatar);
    }
    @Override
    public int getItemCount() {
        return friendList == null ? 0 : friendList.size();
    }

    public static class SocialViewHolder extends RecyclerView.ViewHolder {
        ImageView imgSongCover, imgFriendAvatar;
        TextView tvFriendName, tvSongTitle;

        public SocialViewHolder(@NonNull View itemView) {
            super(itemView);
            imgSongCover = itemView.findViewById(R.id.img_social_song_cover);
            imgFriendAvatar = itemView.findViewById(R.id.img_social_friend_avatar);
            tvFriendName = itemView.findViewById(R.id.tv_social_friend_name);
            tvSongTitle = itemView.findViewById(R.id.tv_social_song_title);
        }
    }
}