package com.example.pulsediagnosis;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class GuardianSelection extends AppCompatActivity {

    private TextView tvNoResults;

    private com.google.android.material.textfield.TextInputEditText etSearchGuardian;

    private Button btnSearchGuardian;

    private RecyclerView rvGuardianResults;

    private ArrayList<GuardianUser> guardianList;

    private GuardianAdapter adapter;

    private DatabaseReference usersRef;
    private DatabaseReference requestsRef;

    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_guardian_selection);

        // -----------------------------
        // Initialize views
        // -----------------------------

        etSearchGuardian =
                findViewById(R.id.etSearchGuardian);

        btnSearchGuardian =
                findViewById(R.id.btnSearchGuardian);

        rvGuardianResults =
                findViewById(R.id.rvGuardianResults);

        tvNoResults =
                findViewById(R.id.tvNoResults);

        // -----------------------------
        // Firebase
        // -----------------------------

        usersRef = FirebaseDatabase
                .getInstance()
                .getReference("Users");

        requestsRef = FirebaseDatabase
                .getInstance()
                .getReference("ConnectionRequests");

        // -----------------------------
        // Session
        // -----------------------------

        session = new SessionManager(this);

        // -----------------------------
        // RecyclerView
        // -----------------------------

        guardianList = new ArrayList<>();

        adapter = new GuardianAdapter(
                guardianList,
                this::sendConnectionRequest
        );

        rvGuardianResults.setLayoutManager(
                new LinearLayoutManager(this)
        );

        rvGuardianResults.setAdapter(adapter);

        // -----------------------------
        // Search
        // -----------------------------

        btnSearchGuardian.setOnClickListener(v ->
                searchGuardians()
        );
    }

    private void searchGuardians() {

        String search =
                String.valueOf(etSearchGuardian.getText())
                        .trim()
                        .toLowerCase(java.util.Locale.ROOT);

        if (TextUtils.isEmpty(search)) {

            Toast.makeText(
                    this,
                    "Enter a name or email",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        guardianList.clear();

        usersRef.get()
                .addOnSuccessListener(snapshot -> {

                    String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();

                    for (DataSnapshot userSnapshot :
                            snapshot.getChildren()) {

                        String uid =
                                userSnapshot.getKey();

                        // Do not show yourself
                        if (uid != null &&
                                uid.equals(currentUserId)) {
                            continue;
                        }

                        String role =
                                userSnapshot
                                        .child("role")
                                        .getValue(String.class);

                        // Only GUARDIAN accounts
                        if (!Boolean.TRUE.equals(userSnapshot.child("guardianEnabled").getValue(Boolean.class)) && !"GUARDIAN".equalsIgnoreCase(role)) {
                            continue;
                        }

                        String name =
                                userSnapshot
                                        .child("name")
                                        .getValue(String.class);

                        String email =
                                userSnapshot
                                        .child("email")
                                        .getValue(String.class);

                        String phone =
                                userSnapshot
                                        .child("phone")
                                        .getValue(String.class);

                        if (name == null) {
                            name = "";
                        }

                        if (email == null) {
                            email = "";
                        }

                        // Search by name OR email
                        if (name.toLowerCase(java.util.Locale.ROOT)
                                .contains(search)
                                ||
                                email.toLowerCase(java.util.Locale.ROOT)
                                        .contains(search)) {

                            GuardianUser guardian =
                                    new GuardianUser(
                                            uid,
                                            name,
                                            email,
                                            phone,
                                            role
                                    );

                            guardianList.add(
                                    guardian
                            );
                        }
                    }

                    adapter.notifyDataSetChanged();

                    if (guardianList.isEmpty()) {

                        tvNoResults.setVisibility(
                                TextView.VISIBLE
                        );

                    } else {

                        tvNoResults.setVisibility(
                                TextView.GONE
                        );
                    }

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            "Search failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void sendConnectionRequest(GuardianUser guardian) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { Toast.makeText(this, "Please log in again", Toast.LENGTH_LONG).show(); return; }
        java.util.Map<String, Object> args = new java.util.HashMap<>(); args.put("guardianId", guardian.getUid());
        GuardianApi.call("sendGuardianRequest", args)
                .addOnSuccessListener(result -> Toast.makeText(this, "Request ready. Ask the guardian to open their dashboard and accept it.", Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Request failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
