package com.example.pulsediagnosis;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Flask extends AppCompatActivity {

    private static final String PREDICTION_URL =
            "https://pulse-diagnosis-backend-render.onrender.com/api/v1/predict";

    private static final String API_KEY =
            "sm0399";

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private EditText etMean;
    private EditText etStd;
    private EditText etVariance;
    private EditText etMin;
    private EditText etMax;
    private EditText etEnergy;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flask);

        etMean = findViewById(R.id.etMean);
        etStd = findViewById(R.id.etStd);
        etVariance = findViewById(R.id.etVariance);
        etMax = findViewById(R.id.etMax);
        etMin = findViewById(R.id.etMin);
        etEnergy = findViewById(R.id.etEnergy);

        View btnSend = findViewById(R.id.btnSend);

        btnSend.setOnClickListener(v -> sendDataToML());
    }

    private void sendDataToML() {

        if (
                etMean.getText().toString().trim().isEmpty() ||
                        etStd.getText().toString().trim().isEmpty() ||
                        etVariance.getText().toString().trim().isEmpty() ||
                        etMax.getText().toString().trim().isEmpty() ||
                        etMin.getText().toString().trim().isEmpty() ||
                        etEnergy.getText().toString().trim().isEmpty()
        ) {

            Toast.makeText(
                    this,
                    "Please fill all fields",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        try {

            double mean = Double.parseDouble(etMean.getText().toString().trim());
            double std = Double.parseDouble(etStd.getText().toString().trim());
            double variance = Double.parseDouble(etVariance.getText().toString().trim());
            double max = Double.parseDouble(etMax.getText().toString().trim());
            double min = Double.parseDouble(etMin.getText().toString().trim());
            double energy = Double.parseDouble(etEnergy.getText().toString().trim());

            double range = max - min;

            JSONObject json = new JSONObject();

            json.put("mean", mean);
            json.put("std", std);
            json.put("variance", variance);
            json.put("max", max);
            json.put("min", min);
            json.put("range", range);
            json.put("energy", energy);

            Log.d("REQUEST_JSON", json.toString());

            RequestBody body =
                    RequestBody.create(json.toString(), JSON);

            Request request = new Request.Builder()
                    .url(PREDICTION_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-api-key", "sm0399")
                    .post(body)
                    .build();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {

                    Log.e("NETWORK_ERROR",
                            e.getMessage(),
                            e);

                    runOnUiThread(() ->
                            Toast.makeText(
                                    Flask.this,
                                    "Network Error: " + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show()
                    );
                }

                @Override
                public void onResponse(Call call, Response response)
                        throws IOException {

                    String responseBody = "";

                    if (response.body() != null) {
                        responseBody = response.body().string();
                    }

                    Log.d("HTTP_CODE",
                            String.valueOf(response.code()));

                    Log.d("SERVER_RESPONSE",
                            responseBody);

                    final String finalResponseBody = responseBody;

                    runOnUiThread(() -> {

                        try {

                            if (!response.isSuccessful()) {

                                Toast.makeText(
                                        Flask.this,
                                        "HTTP Error: "
                                                + response.code(),
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            JSONObject obj =
                                    new JSONObject(finalResponseBody);

                            if (obj.has("error")) {

                                Toast.makeText(
                                        Flask.this,
                                        "Server Error: "
                                                + obj.getString("error"),
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            int prediction =
                                    obj.optInt("prediction_id", 0);

                            String status =
                                    obj.optString("prediction", "Unknown");

                            String risk =
                                    obj.optString("risk_level", "Unknown");

                            double confidence =
                                    obj.optDouble("confidence", 0.0);

                            String recommendation =
                                    obj.optString("recommendation", "");

DiagnosisHistoryStore.save(Flask.this, obj, json);
// Save diagnosis result
                            SharedPreferences prefs =
                                    getSharedPreferences("DiagnosisData", MODE_PRIVATE);

                            prefs.edit()
                                    .putInt("prediction", prediction)
                                    .putString("status", status)
                                    .putString("risk", risk)
                                    .putFloat("confidence", (float) confidence)
                                    .putString("recommendation", recommendation)
                                    .apply();

                            Toast.makeText(
                                    Flask.this,
                                    "Diagnosis Saved Successfully",
                                    Toast.LENGTH_LONG
                            ).show();

// Return to Home screen
                            Intent intent = new Intent(Flask.this, Home.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                            finish();

                        } catch (Exception e) {

                            Log.e(
                                    "JSON_PARSE_ERROR",
                                    e.getMessage(),
                                    e
                            );
                            if (!response.isSuccessful()) {

                                Toast.makeText(
                                        Flask.this,
                                        "HTTP Error: " + response.code() +
                                                "\n" + finalResponseBody,
                                        Toast.LENGTH_LONG
                                ).show();

                            }
                        }
                    });
                }
            });

        } catch (NumberFormatException e) {

            Toast.makeText(
                    this,
                    "Please enter valid numeric values",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {

            Log.e(
                    "REQUEST_ERROR",
                    e.getMessage(),
                    e
            );

            Toast.makeText(
                    this,
                    "Request Error: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}