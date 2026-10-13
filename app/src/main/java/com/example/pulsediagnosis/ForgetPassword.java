package com.example.pulsediagnosis;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ForgetPassword extends AppCompatActivity {

    EditText edtEmail;
    Button btnSendLink;
    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forget_password);

        auth = FirebaseAuth.getInstance();

        edtEmail = findViewById(R.id.edtEmail);
        btnSendLink = findViewById(R.id.btnSendLink);

        btnSendLink.setOnClickListener(v -> sendResetLink());
    }

    private void sendResetLink() {
        String email = edtEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Enter registered email", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(
                            this,
                            "Password reset link sent. Check your email.",
                            Toast.LENGTH_LONG
                    ).show();
                    startActivity(new Intent(this, Settings.class));

                    finish(); // back to Login
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
    }
}
