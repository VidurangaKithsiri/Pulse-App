package com.example.pulsediagnosis;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class DoctorAdapter
        extends RecyclerView.Adapter<DoctorAdapter.ViewHolder> {

    private final ArrayList<Doctor> doctorList;

    public DoctorAdapter(Context context, ArrayList<Doctor> doctorList) {
        this.doctorList = doctorList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_doctor, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position
    ) {
        Doctor doctor = doctorList.get(position);

        // Clear recycled content if Firebase returned a null record.
        if (doctor == null) {
            holder.tvName.setText("");
            holder.tvEmail.setText("");
            holder.tvContact.setText("");
            holder.tvAbout.setText("");
            holder.itemView.setOnClickListener(null);
            return;
        }

        holder.tvName.setText(doctor.name);
        holder.tvEmail.setText(doctor.email);
        holder.tvContact.setText(doctor.contact);
        holder.tvAbout.setText(doctor.about);

        holder.itemView.setOnClickListener(v -> {
            Context clickContext = v.getContext();

            String contact = doctor.contact == null
                    ? ""
                    : doctor.contact.trim();

            if (contact.isEmpty()) {
                Toast.makeText(
                        clickContext,
                        "Doctor contact number is unavailable",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            // Dial the contact number, not the about field.
            Intent intent = new Intent(
                    Intent.ACTION_DIAL,
                    Uri.fromParts("tel", contact, null)
            );

            try {
                clickContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(
                        clickContext,
                        "No dialer app is available",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return doctorList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView tvName;
        final TextView tvEmail;
        final TextView tvAbout;
        final TextView tvContact;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            tvName = itemView.findViewById(R.id.tvName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvAbout = itemView.findViewById(R.id.tvAbout);
            tvContact = itemView.findViewById(R.id.tvContact);

            tvName.setTextColor(Color.BLACK);
            tvEmail.setTextColor(Color.BLACK);
            tvAbout.setTextColor(Color.BLACK);
            tvContact.setTextColor(Color.BLACK);
        }
    }
}