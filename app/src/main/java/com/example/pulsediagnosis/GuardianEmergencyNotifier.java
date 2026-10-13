package com.example.pulsediagnosis;

import android.content.Context;
import android.os.SystemClock;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import java.util.*;

/** Prototype thresholds, mirrored and enforced on the server. */
public final class GuardianEmergencyNotifier {
    public static final double LOW_BPM = 40, HIGH_BPM = 140;
    private final Context context;
    private long abnormalSince = -1, lastSample = -1, lastSent = -1;
    private int abnormalSamples;
    private boolean sending;
    private double latestBpm = Double.NaN;
    public GuardianEmergencyNotifier(Context context) { this.context = context.getApplicationContext(); }
    public synchronized void observeBpm(double bpm) {
        long now = SystemClock.elapsedRealtime();
        if (Double.isNaN(bpm) || Double.isInfinite(bpm) || bpm < 20 || bpm > 250) { resetSignal(); return; }
        latestBpm = bpm;
        boolean abnormal = bpm < LOW_BPM || bpm > HIGH_BPM;
        if (!abnormal) { abnormalSince = -1; abnormalSamples = 0; lastSample = now; return; }
        if (abnormalSince < 0 || lastSample < 0 || now - lastSample > 5000) { abnormalSince = now; abnormalSamples = 0; }
        lastSample = now; abnormalSamples++;
        if (abnormalSamples >= 3 && now - abnormalSince >= 10000 && !sending && (lastSent < 0 || now - lastSent >= 120000))
            send("Sustained abnormal pulse reading", bpm, "SENSOR_BPM", now - abnormalSince, abnormalSamples);
    }
    public synchronized void resetSignal() { abnormalSince = -1; lastSample = -1; abnormalSamples = 0; latestBpm = Double.NaN; }
    public synchronized void manualEmergency() {
        double bpm = lastSample >= 0 && SystemClock.elapsedRealtime() - lastSample <= 15000 ? latestBpm : Double.NaN;
        send("Patient requested emergency assistance", bpm, "MANUAL", 0, 0);
    }
    private synchronized void send(String reason, double bpm, String source, long duration, int samples) {
        if (sending) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) { toast("Please log in to send an alert."); return; }
        sending = true;
        Map<String, Object> args = new HashMap<>(); args.put("eventId", UUID.randomUUID().toString());
        args.put("source", source); args.put("reason", reason); args.put("durationMs", duration); args.put("sampleCount", samples);
        if (!Double.isNaN(bpm) && !Double.isInfinite(bpm)) args.put("bpm", bpm);
        GuardianApi.call("sendGuardianEmergency", args).addOnSuccessListener(result -> {
            synchronized (GuardianEmergencyNotifier.this) { sending = false; lastSent = SystemClock.elapsedRealtime(); }
            Object value = result.getData();
            if (value instanceof Map) {
                Map<?, ?> data = (Map<?, ?>) value;
                if ("COOLDOWN".equals(data.get("status"))) toast("An alert was already queued recently. Please contact your guardian directly if needed.");
                else toast("Emergency saved for " + data.get("guardianCount") + " guardian(s). They can view it in Home → Guardians.");
            } else toast("Emergency alert saved.");
        }).addOnFailureListener(e -> {
            synchronized (GuardianEmergencyNotifier.this) { sending = false; lastSent = SystemClock.elapsedRealtime(); }
            toast("Emergency alert failed: " + e.getMessage());
        });
    }
    private void toast(String message) { new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show()); }
}
