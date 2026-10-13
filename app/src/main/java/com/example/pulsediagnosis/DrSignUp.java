package com.example.pulsediagnosis;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class DrSignUp extends AppCompatActivity {

    public static class Doctor {
        public String name;
        public String email;
        public String contact;
        public String about;
        public String id;

        public Doctor() {}

        public Doctor(String name, String email, String contact, String about, String id) {
            this.name = name;
            this.email = email;
            this.contact = contact;
            this.about = about;
            this.id = id;
        }
    }

    EditText etName, etEmail, etContact, etAbout, etId, etPw;
    View btnSignup;
    android.widget.CheckBox guardianAccount;

    FirebaseAuth auth;
    DatabaseReference doctorsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dr_sign_up);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etContact = findViewById(R.id.etContact);
        etAbout = findViewById(R.id.etAbout);
        etId = findViewById(R.id.etID);
        etPw = findViewById(R.id.etPw);
        btnSignup = findViewById(R.id.btnSignup);
        guardianAccount = findViewById(R.id.guardianAccount);

        auth = FirebaseAuth.getInstance();
        doctorsRef = FirebaseDatabase.getInstance().getReference("Doctors");

        btnSignup.setOnClickListener(v -> registerDoctor());
    }

    private void registerDoctor() {

        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String contact = etContact.getText().toString().trim();
        String about = etAbout.getText().toString().trim();
        String id = etId.getText().toString().trim();
        String password = etPw.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = auth.getCurrentUser();
                    if (firebaseUser == null) {
                        Toast.makeText(this, "Auth error. Try again.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String uid = firebaseUser.getUid();
                    Doctor doctor = new Doctor(name, email, contact, about, id);

                    java.util.Map<String,Object> updates = new java.util.HashMap<>();
                    updates.put("Doctors/" + uid, doctor);
                    java.util.Map<String,Object> profile = new java.util.HashMap<>();
                    profile.put("name", name); profile.put("email", email); profile.put("phone", contact);
                    profile.put("role", "DOCTOR"); profile.put("guardianEnabled", guardianAccount.isChecked());
                    updates.put("Users/" + uid, profile);
                    FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Doctor registered successfully", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(this, Login.class));
                                finish();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Database error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Signup failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
