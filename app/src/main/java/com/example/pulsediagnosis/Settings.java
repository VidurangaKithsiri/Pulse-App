package com.example.pulsediagnosis;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Settings extends AppCompatActivity {

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    Switch switchNotification;
    SharedPreferences prefs;

    SearchView searchBar;
    ScrollView scrollView;

    TextView privacySettings, deleteAccount;

    FirebaseAuth firebaseAuth;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }


        // Firebase
        firebaseAuth = FirebaseAuth.getInstance();

        // Initialize views
        switchNotification = findViewById(R.id.switch1);
        searchBar = findViewById(R.id.SearchBar);
        scrollView = findViewById(R.id.scrollView);

        privacySettings = findViewById(R.id.textView13);
        deleteAccount = findViewById(R.id.textView15);

        // -------------------------------
        // Privacy Settings click
        // -------------------------------
        privacySettings.setOnClickListener(v ->
                startActivity(new Intent(this, PrivacySettings.class)));

        // -------------------------------
        // Delete Account (Firebase)
        // -------------------------------
        deleteAccount.setOnClickListener(v -> confirmDeleteAccount());

        // -------------------------------
        // Notification switch
        // -------------------------------
        prefs = getSharedPreferences("SettingsPrefs", MODE_PRIVATE);

        boolean isOn = prefs.getBoolean("notifications", false);
        switchNotification.setChecked(isOn);

        switchNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("notifications", isChecked).apply();
            Toast.makeText(this,
                    isChecked ? "Notifications ON" : "Notifications OFF",
                    Toast.LENGTH_SHORT).show();
        });

        // -------------------------------
        // Search logic
        // -------------------------------
        searchBar.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                handleSearch(query.toLowerCase());
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                handleSearch(newText.toLowerCase());
                return true;
            }
        });
    }

    // ===============================
    // FIREBASE DELETE ACCOUNT
    // ===============================
    private void confirmDeleteAccount() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("This action is permanent and cannot be undone.\n\nContinue?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Try delete directly
                    user.delete()
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_LONG).show();
                                firebaseAuth.signOut();
                                startActivity(new Intent(this, Login.class));
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("requires recent login")) {
                                    promptReAuthDelete();
                                } else {
                                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptReAuthDelete() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Re-enter your password to delete account");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            String currentPassword = input.getText().toString().trim();
            reAuthAndDelete(currentPassword);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void reAuthAndDelete(String currentPassword) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) return;

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(unused ->
                        user.delete()
                                .addOnSuccessListener(u -> {
                                    Toast.makeText(this, "Account deleted permanently", Toast.LENGTH_LONG).show();
                                    firebaseAuth.signOut();
                                    startActivity(new Intent(this, Login.class));
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show())
                )
                .addOnFailureListener(e -> Toast.makeText(this, "Re-authentication failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }


    // ===============================
    // SEARCH HANDLER
    // ===============================
    private void handleSearch(String text) {

        text = text.trim().toLowerCase();

        privacySettings.setBackgroundColor(Color.TRANSPARENT);
        deleteAccount.setBackgroundColor(Color.TRANSPARENT);
        switchNotification.setBackgroundColor(Color.TRANSPARENT);

        if (text.isEmpty()) return;

        if (matches(text, "n", "no", "not", "notify", "notification")) {
            scrollToView(switchNotification);
            switchNotification.setBackgroundColor(Color.parseColor("#DDE1FF"));
        }
        else if (matches(text, "p", "pri", "privacy", "pass", "password", "lock", "security")) {
            scrollToView(privacySettings);
            privacySettings.setBackgroundColor(Color.parseColor("#DDE1FF"));
        }
        else if (matches(text, "d", "del", "delete", "remove", "clear", "account")) {
            scrollToView(deleteAccount);
            deleteAccount.setBackgroundColor(Color.parseColor("#FFD6D6"));
        }
    }

    private boolean matches(String input, String... keywords) {
        for (String key : keywords) {
            if (key.startsWith(input) || input.startsWith(key)) {
                return true;
            }
        }
        return false;
    }

    // ===============================
    // SCROLL TO VIEW
    // ===============================
    private void scrollToView(View view) {
        scrollView.post(() ->
                scrollView.smoothScrollTo(0, view.getTop())
        );
    }
}
