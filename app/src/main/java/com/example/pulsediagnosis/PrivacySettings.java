package com.example.pulsediagnosis;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class PrivacySettings extends AppCompatActivity {

    LinearLayout OldPwLy, NewPwLy;
    EditText edtPassword, edtNewPassword;
    Button btnSavePassword, btnSaveNewPassword;
    TextView txtForgetPassword;

    FirebaseAuth auth;
    FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_privacy_settings);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        // ---------------- Views ----------------
        OldPwLy = findViewById(R.id.OldPwLy);
        NewPwLy = findViewById(R.id.NewPwLy);
        edtPassword = findViewById(R.id.edtPassword);
        edtNewPassword = findViewById(R.id.edtNewPassword);
        btnSavePassword = findViewById(R.id.btnSavePassword);
        btnSaveNewPassword = findViewById(R.id.btnSaveNewPassword);
        txtForgetPassword = findViewById(R.id.txtForgetPassword);

        // ---------------- Verify Old Password ----------------
        btnSavePassword.setOnClickListener(v -> {
            String oldPassword = edtPassword.getText().toString().trim();
            if (TextUtils.isEmpty(oldPassword)) {
                Toast.makeText(this, "Enter your current password", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyOldPassword(oldPassword);
        });


        // ---------------- Save New Password ----------------
        btnSaveNewPassword.setOnClickListener(v -> {
            String newPassword = edtNewPassword.getText().toString().trim();
            if (TextUtils.isEmpty(newPassword) || newPassword.length() < 6) {
                Toast.makeText(this, "New password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            updateNewPassword(newPassword);
        });

        // ---------------- Forget Password ----------------
       txtForgetPassword.setOnClickListener(v -> {
            startActivity(new Intent(this, ForgetPassword.class));
        });
    }

    // ---------------- Verify Old Password ----------------
    private void verifyOldPassword(String oldPassword) {
        if (currentUser.getEmail() == null) return;

        // Re-authenticate
        currentUser.reauthenticate(EmailAuthProvider.getCredential(currentUser.getEmail(), oldPassword))
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Password verified!", Toast.LENGTH_SHORT).show();

                    // Show new password layout, hide old password layout
                    OldPwLy.setVisibility(View.GONE);
                    NewPwLy.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
                });
    }

    // ---------------- Update New Password ----------------
    private void updateNewPassword(String newPassword) {
        currentUser.updatePassword(newPassword)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show();

                    // Reset layouts
                    edtPassword.setText("");
                    edtNewPassword.setText("");
                    OldPwLy.setVisibility(View.VISIBLE);
                    NewPwLy.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update password: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
