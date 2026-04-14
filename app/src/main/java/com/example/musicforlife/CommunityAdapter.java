package com.example.musicforlife;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CommunityAdapter extends RecyclerView.Adapter<CommunityAdapter.CommunityViewHolder> {

    private Context context;
    private List<CommunityItem> communityItems;

    public CommunityAdapter(Context context, List<CommunityItem> communityItems) {
        this.context = context;
        this.communityItems = communityItems;
    }

    @NonNull
    @Override
    public CommunityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_community, parent, false);
        return new CommunityViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommunityViewHolder holder, int position) {
        CommunityItem item = communityItems.get(position);
        if (item == null) return;

        holder.tvUsername.setText(item.getUsername());
        holder.tvCaption.setText(item.getCaption());
        holder.tvLikeCount.setText(String.valueOf(item.getLikeCount()));
        holder.tvCommentCount.setText(String.valueOf(item.getCommentCount()));
        holder.tvViewAllComments.setText("Xem tất cả " + item.getCommentCount() + " bình luận");

        String avatarUrl = (item.getAvatarUrl() != null) ? item.getAvatarUrl() : "";
        GlideHelper.loadCircleForAdapter(context, avatarUrl, holder.imgAvatar);

        String coverUrl = (item.getCoverUrl() != null) ? item.getCoverUrl() : "";
        GlideHelper.loadCenterCropForAdapter(context, coverUrl, holder.imgCover);

        View.OnClickListener openProfileListener = v -> {
            android.content.SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            String myUsername = prefs.getString("userUsername", "");
            String postAuthorUsername = item.getUsername();

            if (postAuthorUsername != null && postAuthorUsername.equals(myUsername)) {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).switchToAccountTab();
                }
            } else {
                Intent intent = new Intent(context, OtherUserProfileActivity.class);
                intent.putExtra("TARGET_USERNAME", postAuthorUsername);
                context.startActivity(intent);
            }
        };

        holder.imgAvatar.setOnClickListener(openProfileListener);
        holder.tvUsername.setOnClickListener(openProfileListener);
    }

    @Override
    public int getItemCount() {
        return communityItems != null ? communityItems.size() : 0;
    }

    public class CommunityViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAvatar, imgCover;
        TextView tvUsername, tvCaption, tvLikeCount, tvCommentCount, tvViewAllComments;
        ImageButton btnPlay, btnLike, btnComment, btnShare;

        public CommunityViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.img_feed_avatar);
            imgCover = itemView.findViewById(R.id.img_feed_cover);

            tvUsername = itemView.findViewById(R.id.tv_feed_username);
            tvCaption = itemView.findViewById(R.id.tv_feed_caption);
            tvLikeCount = itemView.findViewById(R.id.tv_feed_like_count);
            tvCommentCount = itemView.findViewById(R.id.tv_feed_comment_count);
            tvViewAllComments = itemView.findViewById(R.id.tv_view_all_comments);

            btnPlay = itemView.findViewById(R.id.btn_feed_play_center);
            btnLike = itemView.findViewById(R.id.btn_feed_like);
            btnComment = itemView.findViewById(R.id.btn_feed_comment);
            btnShare = itemView.findViewById(R.id.btn_feed_share);
        }
    }
}