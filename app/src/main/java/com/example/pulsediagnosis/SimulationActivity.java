package com.example.pulsediagnosis;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Replays synthetic assets only. No Bluetooth, Firebase writes or patient alerts. */
public class SimulationActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS).callTimeout(150, TimeUnit.SECONDS).build();
    private final String[] kinds = {"regular", "slow", "fast", "irregular", "noise", "flat", "dropout"};
    private Spinner choice;
    private EditText server, key;
    private TextView status, bpm;
    private Button start;
    private WaveView chart;
    private double[] samples;
    private long began;
    private int generation;
    private boolean running;
    private Call active;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            int count = Math.min(3000, (int)((SystemClock.elapsedRealtime() - began) / 10));
            chart.count = count; chart.invalidate();
            status.setText(String.format(Locale.US, "Simulation replay: %.1f / 30 seconds", count/100.0));
            if (count < 3000) handler.postDelayed(this, 40);
            else { running = false; submit(); }
        }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad,pad,pad,pad); scroll.addView(box); setContentView(scroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        TextView banner = new TextView(this); banner.setText("SIMULATION — SYNTHETIC DATA\nExperimental pulse patterns, not a diagnosis");
        banner.setTextSize(20); banner.setTextColor(Color.rgb(155,65,0)); box.addView(banner);
        choice = new Spinner(this); choice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,kinds)); box.addView(choice);
        server = new EditText(this); server.setSingleLine(true); server.setHint("HTTPS backend origin");
        server.setText(getPreferences(MODE_PRIVATE).getString("server","https://pulse-diagnosis-backend-render.onrender.com")); box.addView(server);
        key = new EditText(this); key.setSingleLine(true); key.setHint("API_KEY configured on your backend");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); box.addView(key);
        TextView instructions = new TextView(this); instructions.setText("Select a pattern and replay 30 seconds. The same raw waveform is then sent to Flask. BPM is calculated by Flask from the waveform after replay; poor signals show no BPM. No results are saved to patient history."); box.addView(instructions);
        chart = new WaveView(this); box.addView(chart,new LinearLayout.LayoutParams(-1,(int)(210*getResources().getDisplayMetrics().density)));
        bpm = new TextView(this); bpm.setTextSize(22); bpm.setText("BPM: —"); box.addView(bpm);
        status = new TextView(this); status.setText("Ready. Upload the updated backend before testing predictions."); box.addView(status);
        start = new Button(this); start.setText("Start simulation"); start.setOnClickListener(v -> begin()); box.addView(start);
        Button stop = new Button(this); stop.setText("Stop / cancel"); stop.setOnClickListener(v -> cancel("Stopped. No prediction saved.")); box.addView(stop);
        Button back = new Button(this); back.setText("Back"); back.setOnClickListener(v -> finish()); box.addView(back);
    }
    private void begin() {
        cancel("");
        HttpUrl url = HttpUrl.parse(server.getText().toString().trim());
        if (url == null || !url.isHttps() || !url.encodedPath().equals("/") || url.query()!=null || url.fragment()!=null || !url.username().isEmpty() || !url.password().isEmpty()) {
            status.setText("Enter the HTTPS backend origin only, without an API path."); return;
        }
        if (key.getText().toString().trim().isEmpty()) { status.setText("Enter your server API_KEY first."); return; }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("simulation/SYNTHETIC_"+kinds[choice.getSelectedItemPosition()]+".csv")))) {
            reader.readLine(); samples = new double[3000]; String line; int i=0;
            while ((line=reader.readLine())!=null) { if (i>=3000) throw new IOException("Too many samples"); samples[i++]=Double.parseDouble(line.split(",")[3]); }
            if (i!=3000) throw new IOException("Incomplete asset");
            chart.setSamples(samples); bpm.setText("BPM: pending waveform analysis");
            start.setEnabled(false); choice.setEnabled(false); server.setEnabled(false); key.setEnabled(false);
            getPreferences(MODE_PRIVATE).edit().putString("server",server.getText().toString().trim()).apply();
            running=true; began=SystemClock.elapsedRealtime(); handler.post(tick);
        } catch(Exception e) { cancel("Cannot load simulation: "+e.getMessage()); }
    }
    private void submit() {
        final int token=generation;
        try {
            JSONArray values=new JSONArray(); for(double value:samples) values.put(value);
            JSONObject body=new JSONObject().put("source","synthetic").put("sample_rate_hz",100).put("samples",values);
            String origin=server.getText().toString().trim().replaceAll("/+$","");
            Request request=new Request.Builder().url(origin+"/api/v1/simulation/predict")
                    .header("x-api-key",key.getText().toString().trim())
                    .post(RequestBody.create(body.toString(),MediaType.get("application/json; charset=utf-8"))).build();
            status.setText("Analyzing waveform on Flask… Server startup may take a minute.");
            active=client.newCall(request); active.enqueue(new Callback() {
                @Override public void onFailure(Call call,IOException e) { deliver(token,"Request failed: "+e.getMessage(),null); }
                @Override public void onResponse(Call call,Response response) {
                    try(Response r=response) {
                        String text=r.body()==null?"":r.body().string();
                        if(!r.isSuccessful()) throw new IOException("HTTP "+r.code()+". Check deployment and API_KEY.");
                        JSONObject data=new JSONObject(text);
                        if(!data.optBoolean("success") || !data.optBoolean("experimental") || !"synthetic".equals(data.optString("source"))) throw new IOException("Unexpected simulation response");
                        Double value=data.isNull("bpm")?null:data.getDouble("bpm");
                        deliver(token,data.getString("prediction")+"\n"+data.getString("notice"),value);
                    } catch(Exception e) { deliver(token,"Prediction failed: "+e.getMessage(),null); }
                }
            });
        } catch(Exception e) { deliver(token,"Request failed: "+e.getMessage(),null); }
    }
    private void deliver(int token,String message,Double value) {
        handler.post(()->{ if(token!=generation || isFinishing() || isDestroyed()) return;
            active=null; status.setText(message); bpm.setText(value==null?"BPM: unavailable":String.format(Locale.US,"Estimated BPM: %.1f",value)); enable(); });
    }
    private void enable() { start.setEnabled(true); choice.setEnabled(true); server.setEnabled(true); key.setEnabled(true); }
    private void cancel(String message) {
        generation++; running=false; handler.removeCallbacks(tick); if(active!=null) { active.cancel(); active=null; }
        if(status!=null) status.setText(message); if(bpm!=null) bpm.setText("BPM: —"); if(start!=null) enable();
    }
    @Override protected void onStop() { cancel("Paused. Start a new simulation to replay."); super.onStop(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private static class WaveView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); double[] values; int count; double min,max;
        WaveView(Context context) { super(context); setBackgroundColor(Color.rgb(240,247,250)); }
        void setSamples(double[] input) { values=input; count=0; min=input[0]; max=min;
            for(double v:input) { min=Math.min(min,v); max=Math.max(max,v); } invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); if(values==null || count<2) return;
            int first=Math.max(0,count-600); Path path=new Path();
            for(int i=first;i<count;i++) { float x=(i-first)*(getWidth()/600f); float y=max-min<1e-9?getHeight()/2f:(float)(getHeight()-15-(values[i]-min)/(max-min)*(getHeight()-30));
                if(i==first) path.moveTo(x,y); else path.lineTo(x,y); }
            paint.setColor(Color.rgb(0,125,135)); paint.setStrokeWidth(3); paint.setStyle(Paint.Style.STROKE); canvas.drawPath(path,paint);
        }
    }
}
