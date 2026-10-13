package com.example.pulsediagnosis;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class GuardianAdapter
        extends RecyclerView.Adapter<GuardianAdapter.GuardianViewHolder> {

    public interface OnConnectClickListener {
        void onConnectClick(GuardianUser guardian);
    }

    private final List<GuardianUser> guardianList;
    private final OnConnectClickListener listener;

    public GuardianAdapter(
            List<GuardianUser> guardianList,
            OnConnectClickListener listener
    ) {
        this.guardianList = guardianList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public GuardianViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(
                        R.layout.activity_guardian_item,
                        parent,
                        false
                );

        return new GuardianViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull GuardianViewHolder holder,
            int position
    ) {

        GuardianUser guardian = guardianList.get(position);

        holder.tvName.setText(guardian.getName());
        holder.tvEmail.setText(guardian.getEmail());

        holder.btnConnect.setOnClickListener(v ->
                listener.onConnectClick(guardian)
        );
    }

    @Override
    public int getItemCount() {
        return guardianList.size();
    }

    public static class GuardianViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvName;
        TextView tvEmail;
        Button btnConnect;

        public GuardianViewHolder(@NonNull View itemView) {
            super(itemView);

            tvName = itemView.findViewById(
                    R.id.tvGuardianName
            );

            tvEmail = itemView.findViewById(
                    R.id.tvGuardianEmail
            );

            btnConnect = itemView.findViewById(
                    R.id.btnConnect
            );
        }
    }
}
