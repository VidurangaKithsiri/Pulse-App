package com.example.pulsediagnosis;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Sends actual raw IR samples to the shared training/inference preprocessing pipeline. */
public final class AutoPredictionClient {
    public interface Listener { void onResult(String message); }
    public interface PredictionListener { void onPrediction(JSONObject response, JSONObject features); }
    private PredictionListener predictionListener;
    public void setPredictionListener(PredictionListener listener) { predictionListener = listener; }
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS).build();
    private Call active;
    private volatile boolean closed;
    public AutoPredictionClient(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
    }
    private void report(String message) { main.post(() -> { if (!closed) listener.onResult(message); }); }
    public synchronized void submit(String payload) {
        if (closed) return;
        if (active != null) { report("Prediction failed: previous request is still running. Wait for it to finish before starting another capture."); return; }
        try {
            final String ownerUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
            JSONObject features = new JSONObject(payload);
            String[] names = {"mean", "std", "variance", "max", "min", "range", "energy"};
            final String captureId = features.optString("_measurementId", "");
            JSONObject body = new JSONObject();
            if (features.has("samples")) {
                org.json.JSONArray samples = features.getJSONArray("samples");
                if (samples.length() < 320 || samples.length() > 8192) throw new IllegalArgumentException("Invalid raw window length");
                for (int i=0;i<samples.length();i++) {
                    double v=samples.getDouble(i);
                    if (Double.isNaN(v) || Double.isInfinite(v)) throw new IllegalArgumentException("Invalid raw sample");
                }
                body.put("samples", samples); body.put("sample_rate",features.getDouble("sample_rate"));
                body.put("sensor","MAX30102");body.put("pipeline_version","ppg-window-v1");
            } else {
                for (String name : names) {
                    double value=features.getDouble(name);
                    if (Double.isNaN(value)||Double.isInfinite(value)) throw new IllegalArgumentException("Invalid "+name);
                    body.put(name,value);
                }
            }
            Request request = new Request.Builder()
                    .url("https://pulse-diagnosis-backend-render.onrender.com/api/v1/predict")
                    .header("x-api-key", "sm0399")
                    .post(RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8"))).build();
            active = client.newCall(request);
            report("Predicting pulse window...");
            active.enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException error) {
                    synchronized (AutoPredictionClient.this) { active = null; }
                    report("Prediction failed: " + error.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    try (Response result = response) {
                        if (result.body() == null) throw new IOException("Empty response (HTTP " + result.code() + ")");
                        String responseText = result.body().string();
                        JSONObject data;
                        try { data = new JSONObject(responseText); }
                        catch (Exception invalid) { throw new IOException("HTTP " + result.code() + ": backend returned a non-JSON response"); }
                        if (!result.isSuccessful() || !data.optBoolean("success", true) || data.has("error"))
                            throw new IOException("HTTP " + result.code() + ": " + data.optString("message", data.optString("error", "Backend rejected prediction")));
                        String status = data.getString("prediction");
                        if (!captureId.isEmpty()) data.put("measurementId", captureId);
                        if (closed || ownerUid == null || !ownerUid.equals(com.google.firebase.auth.FirebaseAuth.getInstance().getUid())) return;
                        if (predictionListener == null) DiagnosisHistoryStore.save(context, data, data.optJSONObject("features") == null ? body : data.optJSONObject("features"));
                        String risk = data.optString("risk_level", "Unknown");
                        context.getSharedPreferences("DiagnosisData", Context.MODE_PRIVATE).edit()
                                .putInt("prediction", data.optInt("prediction_id", -1))
                                .putString("status", status).putString("risk", risk)
                                .putFloat("confidence", (float) data.optDouble("confidence", 0))
                                .putString("recommendation", data.optString("recommendation", "")).apply();
                        StringBuilder featureText = new StringBuilder();
                        featureText.append(" | Windows: ").append(data.optInt("window_count",1));
                        report("Prediction: " + status + " | Risk: " + risk + featureText);
                        if (predictionListener != null) main.post(() -> { if (!closed && ownerUid.equals(com.google.firebase.auth.FirebaseAuth.getInstance().getUid())) predictionListener.onPrediction(data, data.optJSONObject("features") == null ? body : data.optJSONObject("features")); });
                    } catch (Exception error) { report("Prediction failed: " + error.getMessage()); }
                    finally { synchronized (AutoPredictionClient.this) { active = null; } }
                }
            });
        } catch (Exception error) { active = null; report("Invalid sensor features: " + error.getMessage()); }
    }
    public synchronized void close() { closed = true; if (active != null) active.cancel(); }
}
