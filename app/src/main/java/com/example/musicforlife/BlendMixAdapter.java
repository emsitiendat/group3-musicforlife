package com.example.musicforlife;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BlendMixAdapter extends RecyclerView.Adapter<BlendMixAdapter.BlendViewHolder> {

    private Context context;
    private List<Playlist> blendList;
    private OnBlendClickListener listener;

    public interface OnBlendClickListener {
        void onBlendClick(Playlist playlist);
    }

    public BlendMixAdapter(Context context, List<Playlist> blendList, OnBlendClickListener listener) {
        this.context = context;
        this.blendList = blendList;
        this.listener = listener;
    }

    public void updateData(List<Playlist> newBlends) {
        this.blendList = newBlends;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BlendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_blend_mix, parent, false);
        return new BlendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BlendViewHolder holder, int position) {
        Playlist blendPlaylist = blendList.get(position);

        holder.tvTitle.setText(blendPlaylist.getName());

        holder.imgCover.setImageDrawable(null);
        holder.imgCover.setBackgroundColor(Color.parseColor("#3B2F50"));

        android.content.SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String myUsername = prefs.getString("userUsername", "");

        String ownerAvatar = blendPlaylist.getOwnerAvatar() != null ? blendPlaylist.getOwnerAvatar() : "";
        String partnerAvatar = blendPlaylist.getPartnerAvatar() != null ? blendPlaylist.getPartnerAvatar() : "";

        if (myUsername.equals(blendPlaylist.getOwnerUsername())) {
            GlideHelper.loadCircleForAdapter(context, ownerAvatar, holder.imgMyAvatar);
            GlideHelper.loadCircleForAdapter(context, partnerAvatar, holder.imgFriendAvatar);
        } else {
            GlideHelper.loadCircleForAdapter(context, partnerAvatar, holder.imgMyAvatar);
            GlideHelper.loadCircleForAdapter(context, ownerAvatar, holder.imgFriendAvatar);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBlendClick(blendPlaylist);
            }
        });
    }

    @Override
    public int getItemCount() {
        return blendList != null ? blendList.size() : 0;
    }

    public static class BlendViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover, imgFriendAvatar, imgMyAvatar;
        TextView tvTitle;

        public BlendViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.img_blend_cover);
            imgFriendAvatar = itemView.findViewById(R.id.img_blend_friend_avatar);
            imgMyAvatar = itemView.findViewById(R.id.img_blend_my_avatar);
            tvTitle = itemView.findViewById(R.id.tv_blend_title);
        }
    }
}