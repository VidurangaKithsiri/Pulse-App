package com.example.pulsediagnosis;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

public class Profile extends AppCompatActivity {

    TextView txName, txMail, txAge, txNo, txLang, txContact, txAbout;
    ImageView profileImg;
    Button btnEdit;
    Group patientGroup, doctorGroup;

    SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        session = new SessionManager(this);

        // 🔹 Bind views
        profileImg = findViewById(R.id.profileImg);
        txName = findViewById(R.id.txName);
        txMail = findViewById(R.id.txMail);
        txAge = findViewById(R.id.txAge);
        txNo = findViewById(R.id.txNo);
        txLang = findViewById(R.id.txLang);
        txContact = findViewById(R.id.txContact);
        txAbout = findViewById(R.id.txAbout);

        patientGroup = findViewById(R.id.patientGroup);
        doctorGroup = findViewById(R.id.doctorGroup);

        btnEdit = findViewById(R.id.btnEdit);
        btnEdit.setOnClickListener(v ->
                startActivity(new Intent(Profile.this, EditProfileActivity.class)));

        // 🔹 Load profile based on user type
        if ("PATIENT".equals(session.getUserType())) {
            doctorGroup.setVisibility(View.GONE);
            loadPatient();
        } else if ("DOCTOR".equals(session.getUserType())) {
            patientGroup.setVisibility(View.GONE);
            loadDoctor();
        } else {
            Toast.makeText(this,
                    "Session error: user type missing",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ===================== PATIENT =====================
    private void loadPatient() {
        FirebaseDatabase.getInstance().getReference("Users")
                .child(session.getUserId())
                .get()
                .addOnSuccessListener(s -> {

                    if (!s.exists()) return;

                    txName.setText(getSafe(s, "name"));
                    txMail.setText(getSafe(s, "email"));
                    txAge.setText(getSafe(s, "age"));
                    txNo.setText(getSafe(s, "contact"));
                    txLang.setText(getSafe(s, "language"));

                    loadImage(s);
                });
    }

    // ===================== DOCTOR ======================
    private void loadDoctor() {
        FirebaseDatabase.getInstance().getReference("Doctors")
                .child(session.getUserId())
                .get()
                .addOnSuccessListener(s -> {

                    if (!s.exists()) {
                        Toast.makeText(this,
                                "Doctor profile not found",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    txName.setText(getSafe(s, "name"));
                    txMail.setText(getSafe(s, "email"));
                    txContact.setText(getSafe(s, "contact"));
                    txAbout.setText(getSafe(s, "about"));

                    loadImage(s);
                });
    }

    // ===================== IMAGE =======================
    private void loadImage(DataSnapshot s) {
        String imgUrl = s.child("profileImage").getValue(String.class);

        if (imgUrl != null && !imgUrl.isEmpty()) {
            Glide.with(this)
                    .load(imgUrl)
                    .placeholder(R.drawable.circle_bg)
                    .into(profileImg);
        } else {
            profileImg.setImageResource(R.drawable.circle_bg);
        }
    }

    // ===================== SAFE READ ===================
    private String getSafe(DataSnapshot s, String key) {
        String value = s.child(key).getValue(String.class);
        return value == null || value.isEmpty()
                ? "Not provided"
                : value;
    }
}
