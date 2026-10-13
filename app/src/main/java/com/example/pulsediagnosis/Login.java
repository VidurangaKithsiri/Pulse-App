package com.example.pulsediagnosis;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class Login extends AppCompatActivity {

    EditText etEmail, etPassword, etEmailD, etPasswordD;
    Button btnLogin, btnLoginD, adminBtn, sellerBtn;
    TextView btnSignup, btnDr, txtWelcome;

    ViewFlipper viewFlipper;
    LinearLayout loaderDots;
    TextView dot1, dot2, dot3;

    FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewFlipper = findViewById(R.id.viewFlipper);
        adminBtn = findViewById(R.id.adminBtn);
        sellerBtn = findViewById(R.id.sellerBtn);
        txtWelcome = findViewById(R.id.txtWelcome);

        etEmail = findViewById(R.id.adminUsername);
        etPassword = findViewById(R.id.adminPassword);
        btnLogin = findViewById(R.id.adminLoginBtn);
        btnSignup = findViewById(R.id.txtSignup);

        etEmailD = findViewById(R.id.sellerUsername);
        etPasswordD = findViewById(R.id.sellerPassword);
        btnLoginD = findViewById(R.id.sellerLoginBtn);
        btnDr = findViewById(R.id.txtDrSign);

        loaderDots = findViewById(R.id.loaderDots);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);

        firebaseAuth = FirebaseAuth.getInstance();
        SessionManager session = new SessionManager(this);

        // Resolve the signed-in account from the database even if the saved role is stale.
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null && session.isLoggedIn() && currentUser.getUid().equals(session.getUserId())) {
            if ("DOCTOR".equals(session.getUserType())) {
                startActivity(new Intent(this, Home.class)); finish(); return;
            }
            showLoader();
            FirebaseDatabase.getInstance().getReference("Users").child(currentUser.getUid()).child("role").get()
                    .addOnSuccessListener(snapshot -> {
                        hideLoader(); String role = snapshot.getValue(String.class);
                        if (role == null) { Toast.makeText(this, "Account role missing. Please sign in again.", Toast.LENGTH_LONG).show(); session.logout(); return; }
                        role = role.toUpperCase(java.util.Locale.ROOT);
                        session.saveSession(currentUser.getUid(), role);
                        startActivity(new Intent(this, Home.class)); finish();
                    }).addOnFailureListener(e -> { hideLoader(); Toast.makeText(this, "Account lookup failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
        }

        // Animations
        Animation welcomeAnim = AnimationUtils.loadAnimation(this, R.anim.welcome_anim);
        txtWelcome.startAnimation(welcomeAnim);

        Animation dotAnim1 = AnimationUtils.loadAnimation(this, R.anim.dot_blink);
        Animation dotAnim2 = AnimationUtils.loadAnimation(this, R.anim.dot_blink);
        Animation dotAnim3 = AnimationUtils.loadAnimation(this, R.anim.dot_blink);
        dotAnim2.setStartOffset(200);
        dotAnim3.setStartOffset(400);

        dot1.startAnimation(dotAnim1);
        dot2.startAnimation(dotAnim2);
        dot3.startAnimation(dotAnim3);

        viewFlipper.setDisplayedChild(0);

        // Toggle
        adminBtn.setOnClickListener(v -> {
            viewFlipper.setDisplayedChild(0);
            adminBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1E90FF")));
            adminBtn.setTextColor(Color.WHITE);
            sellerBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
            sellerBtn.setTextColor(Color.parseColor("#333333"));
        });

        sellerBtn.setOnClickListener(v -> {
            viewFlipper.setDisplayedChild(1);
            sellerBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0A3D62")));
            sellerBtn.setTextColor(Color.WHITE);
            adminBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
            adminBtn.setTextColor(Color.parseColor("#333333"));
        });

        // Navigation
        btnSignup.setOnClickListener(v ->
                startActivity(new Intent(Login.this, Signup.class)));
        btnDr.setOnClickListener(v ->
                startActivity(new Intent(Login.this, DrSignUp.class)));

        // Login buttons
        btnLogin.setOnClickListener(v -> loginUser());
        btnLoginD.setOnClickListener(v -> loginDr());
    }

    private void showLoader() {
        loaderDots.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);
        btnLoginD.setEnabled(false);
    }

    private void hideLoader() {
        loaderDots.setVisibility(View.GONE);
        btnLogin.setEnabled(true);
        btnLoginD.setEnabled(true);
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoader();
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users").child(uid);
                    ref.get().addOnSuccessListener(snapshot -> {
                        hideLoader();
                        if (!snapshot.exists()) {
                            Toast.makeText(this, "User not found in DB", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String role = snapshot.child("role").getValue(String.class);
                        if (role == null) role = "PATIENT";
                        role = role.toUpperCase(java.util.Locale.ROOT);
                        new SessionManager(Login.this).saveSession(uid, role);
                        startActivity(new Intent(Login.this, Home.class));
                        finish();
                    }).addOnFailureListener(e -> {
                        hideLoader();
                        Toast.makeText(this, "DB error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }).addOnFailureListener(e -> {
                    hideLoader();
                    Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void loginDr() {
        String email = etEmailD.getText().toString().trim();
        String password = etPasswordD.getText().toString().trim();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoader();
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Doctors").child(uid);
                    ref.get().addOnSuccessListener(snapshot -> {
                        hideLoader();
                        if (!snapshot.exists()) {
                            Toast.makeText(Login.this, "Doctor not found in DB", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        new SessionManager(Login.this).saveSession(uid, "DOCTOR");
                        startActivity(new Intent(Login.this, Home.class));
                        finish();
                    }).addOnFailureListener(e -> {
                        hideLoader();
                        Toast.makeText(this, "DB error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }).addOnFailureListener(e -> {
                    hideLoader();
                    Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
