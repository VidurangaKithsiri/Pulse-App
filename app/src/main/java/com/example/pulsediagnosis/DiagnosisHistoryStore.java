package com.example.pulsediagnosis;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Device-local history, isolated by signed-in user. */
public final class DiagnosisHistoryStore {
    private static final String FILE = "diagnosis_history";
    private static String key(Context context) { return com.google.firebase.auth.FirebaseAuth.getInstance().getUid(); }
    public static synchronized void save(Context context, JSONObject response, JSONObject features) {
        String uid = key(context);
        if (uid == null) return;
        try {
            JSONArray old = load(context);
            JSONObject entry = new JSONObject(response.toString());
            entry.put("timestamp", System.currentTimeMillis());
            entry.put("features", new JSONObject(features.toString()));
            JSONArray next = new JSONArray(); next.put(entry);
            for (int i = 0; i < old.length() && next.length() < 200; i++) {
                JSONObject prior = old.optJSONObject(i);
                if (prior != null && entry.has("measurementId") && entry.optString("measurementId").equals(prior.optString("measurementId"))) continue;
                next.put(old.get(i));
            }
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(uid, next.toString()).apply();
        } catch (Exception e) { android.util.Log.e("DiagnosisHistory", "Could not save result", e); }
    }
    public static synchronized JSONArray load(Context context) {
        String uid = key(context);
        if (uid == null) return new JSONArray();
        try { return new JSONArray(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(uid, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
}
