package com.example.pulsediagnosis;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;

public class Diagnosis extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) { super.onCreate(state); }
    @Override protected void onResume() { super.onResume(); renderHistory(); }
    private void renderHistory() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        list.setPadding(pad, pad, pad, pad); scroll.addView(list); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Diagnosis history"); title.setTextSize(24); title.setTextColor(Color.BLACK); list.addView(title);
        JSONArray history = DiagnosisHistoryStore.load(this);
        if (history.length() == 0) {
            TextView empty = new TextView(this); empty.setText("No saved results yet. Complete a new measurement to start your history.");
            empty.setTextColor(Color.DKGRAY); list.addView(empty); return;
        }
        for (int i = 0; i < history.length(); ++i) {
            JSONObject entry = history.optJSONObject(i); if (entry == null) continue;
            TextView row = new TextView(this); row.setTextSize(16); row.setTextColor(Color.DKGRAY);
            row.setPadding(0, pad, 0, pad);
            String text = DateFormat.getDateTimeInstance().format(new Date(entry.optLong("timestamp"))) +
                    "\nResult: " + entry.optString("prediction", "Unknown") +
                    "\nSource: " + entry.optString("source", "Prediction") +
                    "\nRisk: " + entry.optString("risk_level", "Unknown") +
                    "\nConfidence: " + (entry.isNull("confidence") ? "Not available" : String.format(java.util.Locale.US, "%.2f%%", entry.optDouble("confidence", 0))) +
                    "\n" + entry.optString("recommendation", "");
            row.setText(text + "\nTap to show features"); list.addView(row);
            TextView detail = new TextView(this); detail.setVisibility(android.view.View.GONE);
            JSONObject features = entry.optJSONObject("features");
            StringBuilder values = new StringBuilder();
            if (features != null) for (String name : new String[]{"mean","std","variance","max","min","range","energy"})
                values.append(name).append(": ").append(features.optString(name)).append("\n");
            detail.setTextColor(Color.DKGRAY); detail.setText(values.toString()); list.addView(detail);
            row.setOnClickListener(v -> detail.setVisibility(detail.getVisibility() == android.view.View.GONE ? android.view.View.VISIBLE : android.view.View.GONE));
        }
    }
}
