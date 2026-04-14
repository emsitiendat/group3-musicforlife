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

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private Context context;
    private List<User> userList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onUserClick(User user);
    }

    public UserAdapter(Context context, List<User> userList, OnItemClickListener listener) {
        this.context = context;
        this.userList = userList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User item = userList.get(position);

        String nameToShow = (item.getDisplayName() != null && !item.getDisplayName().isEmpty())
                ? item.getDisplayName() : item.getUsername();

        if ("artist".equals(item.getType())) {
            holder.tvUserName.setText(nameToShow + " 🎵 (Nghệ sĩ)");
        } else {
            holder.tvUserName.setText(nameToShow);
        }

        GlideHelper.loadCircleForAdapter(context, item.getAvatarUrl(), holder.imgAvatar);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return userList != null ? userList.size() : 0;
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAvatar;
        TextView tvUserName;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.img_item_user_avatar);
            tvUserName = itemView.findViewById(R.id.tv_item_user_name);
        }
    }
}