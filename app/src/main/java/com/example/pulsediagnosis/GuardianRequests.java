package com.example.pulsediagnosis;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import java.text.DateFormat;
import java.util.*;

/** Guardian dashboard: requests, linked patients, and emergency inbox. */
public class GuardianRequests extends AppCompatActivity {
    private LinearLayout list, alerts;
    private Query requestsQuery, alertsQuery;
    private ValueEventListener requestsListener, alertsListener;
    private String guardianId;
    private Query sentQuery;
    private ValueEventListener sentListener;
    private DatabaseReference linksRef;
    private ValueEventListener linksListener;
    private final Set<String> busy = new HashSet<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_guardian_requests);
        list = findViewById(R.id.requestsList);
        alerts = findViewById(R.id.guardianAlertsList);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { openLogin(); return; }
        guardianId = user.getUid();
        findViewById(R.id.guardianLogout).setOnClickListener(v ->
                GuardianNotifications.logout(this, () -> {
                    FirebaseAuth.getInstance().signOut();
                    new SessionManager(this).logout(); openLogin();
                }));
        label(list, "Loading requests…");
        FirebaseDatabase.getInstance().getReference("Users").child(guardianId).get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed()) return;
                    android.widget.CheckBox consent = findViewById(R.id.guardianConsent);
                    boolean enabled = Boolean.TRUE.equals(snapshot.child("guardianEnabled").getValue(Boolean.class)) || "GUARDIAN".equalsIgnoreCase(snapshot.child("role").getValue(String.class));
                    consent.setChecked(enabled);
                    if (enabled) GuardianNotifications.setup(this);
                    consent.setOnClickListener(v -> {
                        consent.setEnabled(false); Map<String,Object> args = new HashMap<>(); args.put("enabled", consent.isChecked());
                        GuardianApi.call("setGuardianConsent", args).addOnSuccessListener(result -> {
                            consent.setEnabled(true); if (consent.isChecked()) GuardianNotifications.setup(this);
                        }).addOnFailureListener(e -> { consent.setEnabled(true); consent.setChecked(!consent.isChecked()); toast(e.getMessage()); });
                    });
                    findViewById(R.id.sendGuardianRequest).setOnClickListener(v -> startActivity(new Intent(this, GuardianSelection.class)));
                    listen();
                }).addOnFailureListener(e -> { list.removeAllViews(); label(list, "Unable to load account: " + e.getMessage()); });
    }
    private void openLogin() {
        Intent intent = new Intent(this, Login.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent); finish();
    }
    private TextView label(LinearLayout parent, String text) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(16); view.setTextColor(android.graphics.Color.parseColor("#183344"));
        view.setPadding(8, 16, 8, 8); parent.addView(view); return view;
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void listen() {
        LinearLayout acceptedList = findViewById(R.id.acceptedGuardiansList);
        linksRef = FirebaseDatabase.getInstance().getReference("GuardianLinks").child(guardianId);
        linksListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot data) {
                acceptedList.removeAllViews(); int count = 0;
                for (DataSnapshot link : data.getChildren()) {
                    if (!Boolean.TRUE.equals(link.child("connected").getValue(Boolean.class)) || link.getKey() == null) continue;
                    count++;
                    TextView row = label(acceptedList, "Connected guardian: " + link.getKey());
                    FirebaseDatabase.getInstance().getReference("Users").child(link.getKey()).get()
                        .addOnSuccessListener(profile -> {
                            String name = profile.child("name").getValue(String.class);
                            String email = profile.child("email").getValue(String.class);
                            boolean enabled = Boolean.TRUE.equals(profile.child("guardianEnabled").getValue(Boolean.class)) || "GUARDIAN".equalsIgnoreCase(profile.child("role").getValue(String.class));
                            row.setText((name == null ? "Guardian" : name) + (email == null ? "" : "\n" + email)
                                + "\nConnection: Accepted" + (enabled ? "\nGuardian consent: enabled" : "\nGuardian consent: disabled"));
                        }).addOnFailureListener(e -> row.append("\nCannot load guardian details: " + e.getMessage()));
                }
                if (count == 0) label(acceptedList, "No accepted guardians yet. Send a request and ask the recipient to accept it.");
                if (getIntent().getBooleanExtra("showAcceptedGuardians", false)) {
                    acceptedList.post(() -> acceptedList.requestFocus());
                    getIntent().putExtra("showAcceptedGuardians", false);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { acceptedList.removeAllViews(); label(acceptedList, "Cannot load accepted guardians: " + error.getMessage()); }
        };
        linksRef.addValueEventListener(linksListener);
        LinearLayout sentList = findViewById(R.id.sentRequestsList);
        sentQuery = FirebaseDatabase.getInstance().getReference("ConnectionRequests").orderByChild("patientId").equalTo(guardianId);
        sentListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot data) {
                sentList.removeAllViews();
                if (!data.exists()) label(sentList, "No sent requests");
                for (DataSnapshot row : data.getChildren()) label(sentList, String.valueOf(row.child("guardianName").getValue() == null ? row.child("guardianId").getValue() : row.child("guardianName").getValue()) + " — " + row.child("status").getValue());
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { label(sentList, error.getMessage()); }
        };
        sentQuery.addValueEventListener(sentListener);
        requestsQuery = FirebaseDatabase.getInstance().getReference("ConnectionRequests")
                .orderByChild("guardianId").equalTo(guardianId);
        requestsListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot data) {
                list.removeAllViews(); int pending = 0, linked = 0;
                for (DataSnapshot item : data.getChildren()) {
                    String status = item.child("status").getValue(String.class);
                    if (!"PENDING".equals(status) && !"ACCEPTED".equals(status)) continue;
                    String patient = item.child("patientId").getValue(String.class);
                    if (patient == null || item.getKey() == null) continue;
                    String name = item.child("patientName").getValue(String.class);
                    TextView title = label(list, (name == null ? patient : name) + " — " + status);
                    if (name == null) FirebaseDatabase.getInstance().getReference("Users").child(patient).child("name").get()
                            .addOnSuccessListener(s -> { String value = s.getValue(String.class); if (value != null) title.setText(value + " — " + status); });
                    if ("ACCEPTED".equals(status)) {
                        linked++;
                        Button verify = new Button(GuardianRequests.this); verify.setText("Verify / repair connection"); styleButton(verify); list.addView(verify);
                        verify.setOnClickListener(v -> decide(item.getKey(), "ACCEPTED", verify, verify));
                        continue;
                    }
                    pending++;
                    Button accept = new Button(GuardianRequests.this), reject = new Button(GuardianRequests.this);
                    accept.setText("Accept request"); reject.setText("Reject request"); styleButton(accept); styleButton(reject);
                    list.addView(accept); list.addView(reject);
                    accept.setEnabled(!busy.contains(item.getKey())); reject.setEnabled(!busy.contains(item.getKey()));
                    accept.setOnClickListener(v -> decide(item.getKey(), "ACCEPTED", accept, reject));
                    reject.setOnClickListener(v -> decide(item.getKey(), "REJECTED", accept, reject));
                }
                if (pending == 0) label(list, "No pending requests");
                if (linked == 0) label(list, "No linked patients yet");
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { list.removeAllViews(); label(list, "Cannot read requests: " + e.getMessage()); }
        };
        requestsQuery.addValueEventListener(requestsListener);
        alertsQuery = FirebaseDatabase.getInstance().getReference("GuardianAlerts").child(guardianId)
                .orderByChild("timestamp").limitToLast(50);
        alertsListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot data) {
                alerts.removeAllViews(); List<DataSnapshot> rows = new ArrayList<>();
                for (DataSnapshot row : data.getChildren()) rows.add(row);
                Collections.reverse(rows);
                if (rows.isEmpty()) label(alerts, "No emergency alerts");
                for (DataSnapshot row : rows) {
                    String name = row.child("patientName").getValue(String.class);
                    String reason = row.child("reason").getValue(String.class);
                    Long time = row.child("timestamp").getValue(Long.class);
                    Object bpm = row.child("bpm").getValue();
                    label(alerts, (name == null ? "Patient" : name) + "\n" + (reason == null ? "Emergency alert" : reason)
                            + (bpm == null ? "" : "\nPulse: " + bpm + " BPM")
                            + (time == null ? "" : "\n" + DateFormat.getDateTimeInstance().format(new Date(time)))
                            + "\nAlert delivery: " + String.valueOf(row.child("pushStatus").getValue()));
                    if (!row.child("acknowledgedAt").exists()) {
                        Button acknowledge = new Button(GuardianRequests.this); acknowledge.setText("Acknowledge"); styleButton(acknowledge); alerts.addView(acknowledge);
                        acknowledge.setOnClickListener(v -> {
                            acknowledge.setEnabled(false); Map<String, Object> args = new HashMap<>(); args.put("alertId", row.getKey());
                            GuardianApi.call("acknowledgeGuardianAlert", args).addOnFailureListener(e -> { acknowledge.setEnabled(true); toast(e.getMessage()); });
                        });
                    } else label(alerts, "Acknowledged");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { alerts.removeAllViews(); label(alerts, "Cannot read alerts: " + e.getMessage()); }
        };
        alertsQuery.addValueEventListener(alertsListener);
    }
    private void styleButton(Button button) {
        button.setTextColor(android.graphics.Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1769AA")));
    }
    private void decide(String id, String decision, Button accept, Button reject) {
        if (!busy.add(id)) return;
        accept.setEnabled(false); reject.setEnabled(false);
        Map<String, Object> args = new HashMap<>(); args.put("requestId", id); args.put("decision", decision);
        GuardianApi.call("decideGuardianRequest", args)
                .addOnSuccessListener(result -> { busy.remove(id); toast("ACCEPTED".equals(decision) ? "Patient linked. Emergency alerts enabled." : "Request rejected."); })
                .addOnFailureListener(e -> { busy.remove(id); accept.setEnabled(true); reject.setEnabled(true); toast("Request update failed: " + e.getMessage()); });
    }
    @Override protected void onDestroy() {
        if (linksRef != null && linksListener != null) linksRef.removeEventListener(linksListener);
        if (sentQuery != null && sentListener != null) sentQuery.removeEventListener(sentListener);
        if (requestsQuery != null && requestsListener != null) requestsQuery.removeEventListener(requestsListener);
        if (alertsQuery != null && alertsListener != null) alertsQuery.removeEventListener(alertsListener);
        super.onDestroy();
    }
    @Override protected void onStart() { super.onStart(); GuardianNotifications.start(this); }
    @Override protected void onStop() { GuardianNotifications.stop(this); super.onStop(); }

}
