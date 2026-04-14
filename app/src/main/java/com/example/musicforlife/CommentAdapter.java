package com.example.musicforlife;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private List<Comment> commentList;

    public CommentAdapter(List<Comment> commentList) {
        this.commentList = commentList;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = commentList.get(position);
        holder.tvName.setText(comment.getUser().name_display);
        holder.tvText.setText(comment.getText());

        GlideHelper.loadCircleForAdapter(holder.itemView.getContext(), comment.getUser().avatar_url, holder.imgAvatar);

        if (comment.isOptimistic()) {
            holder.itemView.setAlpha(0.5f);
        } else {
            holder.itemView.setAlpha(1.0f);
        }
        View.OnClickListener openProfileListener = v -> {
            android.content.Context context = holder.itemView.getContext();
            android.content.SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            String myUsername = prefs.getString("userUsername", "");

            String commentAuthorUsername = comment.getUser().username;

            if (commentAuthorUsername != null && commentAuthorUsername.equals(myUsername)) {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).switchToAccountTab();
                }
            } else {
                android.content.Intent intent = new android.content.Intent(context, OtherUserProfileActivity.class);
                intent.putExtra("TARGET_USERNAME", commentAuthorUsername);
                context.startActivity(intent);
            }
        };

        holder.imgAvatar.setOnClickListener(openProfileListener);
        holder.tvName.setOnClickListener(openProfileListener);

    }

    @Override
    public int getItemCount() {
        return commentList == null ? 0 : commentList.size();
    }

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAvatar;
        TextView tvName, tvText;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.img_comment_avatar);
            tvName = itemView.findViewById(R.id.tv_comment_name);
            tvText = itemView.findViewById(R.id.tv_comment_text);
        }
    }
}