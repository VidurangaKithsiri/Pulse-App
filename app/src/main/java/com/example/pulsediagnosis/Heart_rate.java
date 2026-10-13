package com.example.pulsediagnosis;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class Heart_rate extends AppCompatActivity {

    // =========================================================
    // UI
    // =========================================================

    private TextView tvHeartRate;
    private AutoPredictionClient autoPrediction;
    private String diagnosisStatus = "Waiting for a measurement";
    private boolean predictionPending;
    private GuardianEmergencyNotifier guardianEmergency;
    private final RawCaptureAssembler rawCapture = new RawCaptureAssembler();
    private String measurementId = java.util.UUID.randomUUID().toString();
    private org.json.JSONObject lastLiveDiagnosis;
    private final Runnable diagnosisTimeout = () -> {
        if (lastLiveDiagnosis == null) { predictionPending = false; setDiagnosisStatus("Diagnosis timed out. Check phone internet and Render service, then take a new measurement."); }
    };
    private String savedMeasurementId;

    private TextView tvConnectionStatus;

    private LineChart pulseWaveform;

    // =========================================================
    // CHART
    // =========================================================

    private LineDataSet waveformDataSet;

    private LineData waveformLineData;

    private float waveformX = 0f;

    /*
     * ESP32 sends the graph at approximately 32 points/sec.
     *
     * 256 / 32 = approximately 8 seconds visible.
     */
    private static final int MAX_VISIBLE_POINTS = 256;

    /*
     * Keep slightly more points than visible.
     */
    private static final int MAX_STORED_POINTS = 300;

    // =========================================================
    // BLUETOOTH
    // =========================================================

    private BluetoothAdapter bluetoothAdapter;

    private BluetoothDevice esp32Device;

    private BluetoothSocket bluetoothSocket;

    private InputStream inputStream;

    private Thread connectionThread;

    private Thread bluetoothReaderThread;

    private volatile boolean bluetoothRunning = false;

    // =========================================================
    // ESP32 NAME
    // =========================================================

    private static final String ESP32_DEVICE_NAME =
            "PulseDiagnosis_ESP32";

    // =========================================================
    // SERIAL UUID
    // =========================================================

    private static final UUID SERIAL_UUID =
            UUID.fromString(
                    "00001101-0000-1000-8000-00805F9B34FB"
            );

    // =========================================================
    // BLUETOOTH PERMISSION
    // =========================================================

    private static final int BLUETOOTH_PERMISSION_REQUEST =
            1001;

    // =========================================================
    // MAIN UI HANDLER
    // =========================================================

    private final Handler mainHandler =
            new Handler(
                    Looper.getMainLooper()
            );

    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );

        setContentView(
                R.layout.activity_heart_rate
        );

        guardianEmergency = new GuardianEmergencyNotifier(this);
        autoPrediction = new AutoPredictionClient(this, message -> {
            if (message.startsWith("Prediction failed:") || message.startsWith("Invalid sensor features:")) {
                predictionPending = false; mainHandler.removeCallbacks(diagnosisTimeout);
            }
            setDiagnosisStatus(message);
        });
        autoPrediction.setPredictionListener((response, features) -> recordLiveDiagnosis(response, features));

        // =====================================================
        // SYSTEM BAR INSETS
        // =====================================================

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(
                        R.id.main
                ),
                (view, insets) -> {

                    Insets systemBars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                            );

                    view.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );

        // =====================================================
        // FIND UI
        // =====================================================

        tvHeartRate =
                findViewById(
                        R.id.tvHeartRate
                );

        tvConnectionStatus =
                findViewById(
                        R.id.tvConnectionStatus
                );

        pulseWaveform =
                findViewById(
                        R.id.pulseWaveform
                );

        // =====================================================
        // INITIAL UI
        // =====================================================

        tvHeartRate.setText(
                "-- BPM"
        );

        tvConnectionStatus.setText(
                "Connecting to ESP32..."
        );

        // =====================================================
        // SETUP CHART
        // =====================================================

        setupWaveformChart();

        // =====================================================
        // START BLUETOOTH
        // =====================================================

        initializeBluetooth();
    }

    // =========================================================
    // SETUP LIVE WAVEFORM CHART
    // =========================================================

    private void setupWaveformChart() {

        // =====================================================
        // DATA SET
        // =====================================================

        waveformDataSet =
                new LineDataSet(
                        new ArrayList<>(),
                        "Pulse"
                );

        // =====================================================
        // LINE APPEARANCE
        // =====================================================

        waveformDataSet.setColor(
                Color.rgb(
                        211,
                        47,
                        47
                )
        );

        waveformDataSet.setLineWidth(
                2.2f
        );

        // No circles like a crypto chart
        waveformDataSet.setDrawCircles(
                false
        );

        waveformDataSet.setDrawValues(
                false
        );

        waveformDataSet.setDrawFilled(
                false
        );

        waveformDataSet.setHighlightEnabled(
                false
        );

        // LINEAR gives an accurate sensor waveform
        waveformDataSet.setMode(
                LineDataSet.Mode.LINEAR
        );

        // =====================================================
        // LINE DATA
        // =====================================================

        waveformLineData =
                new LineData(
                        waveformDataSet
                );

        pulseWaveform.setData(
                waveformLineData
        );

        // =====================================================
        // DESCRIPTION
        // =====================================================

        Description description =
                new Description();

        description.setText(
                "Live Pulse Signal"
        );

        description.setTextSize(
                9f
        );

        pulseWaveform.setDescription(
                description
        );

        // =====================================================
        // LIVE CHART BEHAVIOUR
        // =====================================================

        pulseWaveform.setTouchEnabled(
                true
        );

        pulseWaveform.setDragEnabled(
                true
        );

        pulseWaveform.setScaleEnabled(
                false
        );

        pulseWaveform.setPinchZoom(
                false
        );

        pulseWaveform.setDoubleTapToZoomEnabled(
                false
        );

        // Important:
        // Y-axis automatically follows pulse amplitude.
        pulseWaveform.setAutoScaleMinMaxEnabled(
                true
        );

        pulseWaveform.setDragDecelerationEnabled(
                false
        );

        // =====================================================
        // X AXIS
        // =====================================================

        XAxis xAxis =
                pulseWaveform.getXAxis();

        xAxis.setPosition(
                XAxis.XAxisPosition.BOTTOM
        );

        // Hide X values
        xAxis.setDrawLabels(
                false
        );

        xAxis.setDrawGridLines(
                false
        );

        xAxis.setDrawAxisLine(
                true
        );

        xAxis.setAxisLineColor(
                Color.LTGRAY
        );

        // =====================================================
        // LEFT Y AXIS
        // =====================================================

        YAxis leftAxis =
                pulseWaveform.getAxisLeft();

        leftAxis.setDrawLabels(
                true
        );

        leftAxis.setDrawGridLines(
                true
        );

        leftAxis.setGridColor(
                Color.LTGRAY
        );

        leftAxis.setAxisLineColor(
                Color.GRAY
        );

        leftAxis.setTextColor(
                Color.DKGRAY
        );

        /*
         * IMPORTANT:
         *
         * No:
         *
         * setAxisMinimum(-5000)
         * setAxisMaximum(2000)
         *
         * We let MPAndroidChart calculate the range.
         */

        leftAxis.resetAxisMinimum();

        leftAxis.resetAxisMaximum();

        leftAxis.setSpaceTop(
                15f
        );

        leftAxis.setSpaceBottom(
                15f
        );

        // =====================================================
        // RIGHT Y AXIS
        // =====================================================

        pulseWaveform
                .getAxisRight()
                .setEnabled(
                        false
                );

        // =====================================================
        // LEGEND
        // =====================================================

        pulseWaveform
                .getLegend()
                .setEnabled(
                        false
                );

        // =====================================================
        // DISPLAY WINDOW
        // =====================================================

        pulseWaveform.setVisibleXRangeMaximum(
                MAX_VISIBLE_POINTS
        );

        pulseWaveform.setVisibleXRangeMinimum(
                50
        );

        // =====================================================
        // NO STARTUP ANIMATION
        // =====================================================

        //pulseWaveform
        //        .getAnimator()
        //        .setDuration(
        //               0,
        //                0
        //        );

        pulseWaveform.invalidate();
    }

    // =========================================================
    // INITIALIZE BLUETOOTH
    // =========================================================

    private void initializeBluetooth() {

        bluetoothAdapter =
                BluetoothAdapter.getDefaultAdapter();

        // =====================================================
        // NO BLUETOOTH
        // =====================================================

        if (
                bluetoothAdapter ==
                        null
        ) {

            updateConnectionStatus(
                    "Bluetooth not supported"
            );

            showToast(
                    "Bluetooth is not supported on this phone."
            );

            return;
        }

        // =====================================================
        // ANDROID 12+
        // =====================================================

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.S
        ) {

            if (
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                    )
                            !=
                            PackageManager.PERMISSION_GRANTED
                            ||
                            ActivityCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.BLUETOOTH_SCAN
                            )
                                    !=
                                    PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                        },
                        BLUETOOTH_PERMISSION_REQUEST
                );

                return;
            }
        }

        // =====================================================
        // BLUETOOTH OFF
        // =====================================================

        if (
                !bluetoothAdapter.isEnabled()
        ) {

            updateConnectionStatus(
                    "Bluetooth is OFF"
            );

            showToast(
                    "Please turn on Bluetooth."
            );

            return;
        }

        // =====================================================
        // CONNECT
        // =====================================================

        connectToESP32();
    }

    // =========================================================
    // PERMISSION RESULT
    // =========================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (
                requestCode ==
                        BLUETOOTH_PERMISSION_REQUEST
        ) {

            boolean granted =
                    true;

            if (
                    grantResults.length ==
                            0
            ) {

                granted =
                        false;
            }

            for (
                    int result :
                    grantResults
            ) {

                if (
                        result !=
                                PackageManager.PERMISSION_GRANTED
                ) {

                    granted =
                            false;

                    break;
                }
            }

            if (granted) {

                initializeBluetooth();

            } else {

                updateConnectionStatus(
                        "Bluetooth permission denied"
                );

                showToast(
                        "Bluetooth permission is required."
                );
            }
        }
    }

    // =========================================================
    // FIND ESP32
    // =========================================================

    private BluetoothDevice findESP32() {

        if (
                bluetoothAdapter ==
                        null
        ) {

            return null;
        }

        // =====================================================
        // ANDROID 12+ PERMISSION
        // =====================================================

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.S
        ) {

            if (
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                    )
                            !=
                            PackageManager.PERMISSION_GRANTED
            ) {

                return null;
            }
        }

        try {

            Set<BluetoothDevice> bondedDevices =
                    bluetoothAdapter.getBondedDevices();

            for (
                    BluetoothDevice device :
                    bondedDevices
            ) {

                String name =
                        device.getName();

                if (
                        name !=
                                null
                                &&
                                name.equals(
                                        ESP32_DEVICE_NAME
                                )
                ) {

                    return device;
                }
            }

        } catch (
                SecurityException e
        ) {

            Log.e(
                    "BLUETOOTH",
                    "Permission error while finding ESP32",
                    e
            );
        }

        return null;
    }

    // =========================================================
    // CONNECT TO ESP32
    // =========================================================

    private void connectToESP32() {

        esp32Device =
                findESP32();

        if (
                esp32Device ==
                        null
        ) {

            updateConnectionStatus(
                    "ESP32 not paired"
            );

            showToast(
                    "Pair PulseDiagnosis_ESP32 with your phone first."
            );

            return;
        }

        connectionThread =
                new Thread(
                        () -> {

                            try {

                                closeBluetoothConnection();

                                // =================================
                                // ANDROID 12 PERMISSION
                                // =================================

                                if (
                                        Build.VERSION.SDK_INT >=
                                                Build.VERSION_CODES.S
                                                &&
                                                ActivityCompat.checkSelfPermission(
                                                        this,
                                                        Manifest.permission.BLUETOOTH_CONNECT
                                                )
                                                        !=
                                                        PackageManager.PERMISSION_GRANTED
                                ) {

                                    return;
                                }

                                // =================================
                                // CANCEL DISCOVERY
                                // =================================

                                if (
                                        bluetoothAdapter.isDiscovering()
                                ) {

                                    bluetoothAdapter.cancelDiscovery();
                                }

                                updateConnectionStatus(
                                        "Connecting..."
                                );

                                // =================================
                                // CREATE SERIAL SOCKET
                                // =================================

                                bluetoothSocket =
                                        esp32Device
                                                .createRfcommSocketToServiceRecord(
                                                        SERIAL_UUID
                                                );

                                bluetoothSocket.connect();

                                inputStream =
                                        bluetoothSocket.getInputStream();

                                bluetoothRunning =
                                        true;

                                updateConnectionStatus(
                                        "ESP32 Connected"
                                );

                                showToast(
                                        "ESP32 connected successfully!"
                                );

                                startBluetoothReader();

                            } catch (
                                    Exception e
                            ) {

                                Log.e(
                                        "BLUETOOTH",
                                        "Connection failed",
                                        e
                                );

                                bluetoothRunning =
                                        false;

                                updateConnectionStatus(
                                        "Connection failed"
                                );

                                showToast(
                                        "Could not connect to ESP32."
                                );

                                updateHeartRate(
                                        "-- BPM"
                                );
                            }
                        }
                );

        connectionThread.start();
    }

    // =========================================================
    // BLUETOOTH READER
    // =========================================================

    private void startBluetoothReader() {

        bluetoothReaderThread =
                new Thread(
                        () -> {

                            StringBuilder buffer =
                                    new StringBuilder();

                            byte[] readBuffer =
                                    new byte[1024];

                            while (
                                    bluetoothRunning
                            ) {

                                try {

                                    if (
                                            inputStream ==
                                                    null
                                    ) {

                                        break;
                                    }

                                    int bytesRead =
                                            inputStream.read(
                                                    readBuffer
                                            );

                                    if (
                                            bytesRead <=
                                                    0
                                    ) {

                                        continue;
                                    }

                                    String incoming =
                                            new String(
                                                    readBuffer,
                                                    0,
                                                    bytesRead,
                                                    StandardCharsets.UTF_8
                                            );

                                    buffer.append(
                                            incoming
                                    );

                                    int newline;

                                    while (
                                            (
                                                    newline =
                                                            buffer.indexOf(
                                                                    "\n"
                                                            )
                                            )
                                                    >=
                                                    0
                                    ) {

                                        String line =
                                                buffer
                                                        .substring(
                                                                0,
                                                                newline
                                                        )
                                                        .trim();

                                        buffer.delete(
                                                0,
                                                newline + 1
                                        );

                                        if (
                                                !line.isEmpty()
                                        ) {

                                            processBluetoothMessage(
                                                    line
                                            );
                                        }
                                    }

                                } catch (
                                        IOException e
                                ) {

                                    Log.e(
                                            "BLUETOOTH",
                                            "Bluetooth disconnected",
                                            e
                                    );

                                    bluetoothRunning =
                                            false;

                                    updateConnectionStatus(
                                            "Bluetooth disconnected"
                                    );

                                    updateHeartRate(
                                            "-- BPM"
                                    );

                                    break;
                                }
                            }
                        }
                );

        bluetoothReaderThread.start();
    }

    // =========================================================
    // PROCESS BLUETOOTH MESSAGE
    // =========================================================

    private void processBluetoothMessage(
            String message
    ) {

        if (Looper.myLooper() != Looper.getMainLooper()) {
            final String frame = message;
            mainHandler.post(() -> processBluetoothMessage(frame)); return;
        }
        message = message.trim();
        if (message.startsWith("RAW_BEGIN:") || message.startsWith("RAW_DATA:") || message.startsWith("RAW_END:")) {
            try {
                String payload = rawCapture.accept(message);
                if (message.startsWith("RAW_BEGIN:")) {
                    predictionPending = true;
                    mainHandler.removeCallbacks(diagnosisTimeout);
                    mainHandler.postDelayed(diagnosisTimeout, 30000);
                    setDiagnosisStatus("Receiving pulse samples...");
                }
                if (payload != null) {
                    org.json.JSONObject capture = new org.json.JSONObject(payload);
                    capture.put("_measurementId", measurementId);
                    predictionPending = true;
                    mainHandler.removeCallbacks(diagnosisTimeout);
                    mainHandler.postDelayed(diagnosisTimeout, 100000);
                    autoPrediction.submit(capture.toString());
                }
            } catch (Exception error) {
                rawCapture.reset(); predictionPending = false;
                mainHandler.removeCallbacks(diagnosisTimeout);
                setDiagnosisStatus("Prediction failed: " + error.getMessage());
            }
            return;
        }
        if (message.startsWith("PREDICTION:")) {
            try {
                org.json.JSONObject data = new org.json.JSONObject(message.substring(11).trim());
                if (!data.has("prediction") || data.has("error") || !data.optBoolean("success", true)) throw new IllegalArgumentException("Invalid prediction response");
                org.json.JSONObject features = data.optJSONObject("features");
                recordLiveDiagnosis(data, features == null ? new org.json.JSONObject() : features);
            } catch (Exception e) { predictionPending = false; mainHandler.removeCallbacks(diagnosisTimeout); setDiagnosisStatus("Prediction failed: " + e.getMessage()); }
            return;
        }
        if (message.startsWith("STATUS:")) {
            final String status = message.substring(7);
            if (lastLiveDiagnosis == null) setDiagnosisStatus(status);
            return;
        }
        if (message.startsWith("FEATURES:") || message.startsWith("RAW_WINDOW:")) {
            try {
                org.json.JSONObject features = new org.json.JSONObject(message.substring(message.indexOf(':') + 1).trim());
                features.put("_measurementId", measurementId);
                predictionPending = true; mainHandler.removeCallbacks(diagnosisTimeout);
                mainHandler.postDelayed(diagnosisTimeout, 100000);
                autoPrediction.submit(features.toString());
            } catch (Exception error) { predictionPending = false; mainHandler.removeCallbacks(diagnosisTimeout); setDiagnosisStatus("Invalid sensor features: " + error.getMessage()); }
            return;
        }

        Log.d(
                "ESP32_DATA",
                message
        );

        // =====================================================
        // WAVEFORM
        //
        // ESP32 now sends:
        //
        // W:-120.5
        // W:35.2
        // W:420.1
        // W:800.3
        // W:250.8
        //
        // Already baseline-removed and filtered.
        // =====================================================

        if (
                message.startsWith(
                        "W:"
                )
        ) {

            try {

                String value =
                        message
                                .substring(
                                        2
                                )
                                .trim();

                float waveformValue =
                        Float.parseFloat(
                                value
                        );

                Log.d(
                        "PULSE_WAVEFORM",
                        "Wave: "
                                +
                                waveformValue
                );

                addWaveformPoint(
                        waveformValue
                );

            } catch (
                    NumberFormatException e
            ) {

                Log.e(
                        "PULSE_WAVEFORM",
                        "Invalid waveform: "
                                +
                                message
                );
            }

            return;
        }

        // =====================================================
        // REAL-TIME BPM
        // =====================================================

        if (
                message.startsWith(
                        "BPM:"
                )
        ) {

            try {

                String value =
                        message
                                .substring(
                                        4
                                )
                                .trim();

                double bpm =
                        Double.parseDouble(
                                value
                        );

                updateHeartRate(
                        String.format(
                                Locale.US,
                                "%.1f BPM",
                                bpm
                        )
                );

            } catch (
                    NumberFormatException e
            ) {

                Log.e(
                        "REAL_TIME_BPM",
                        "Invalid BPM: "
                                +
                                message
                );
            }

            return;
        }

        // =====================================================
        // MEASUREMENT START
        // =====================================================

        if (
                message.equals(
                        "MEASUREMENT_START"
                )
        ) {

            rawCapture.reset();
            measurementId = java.util.UUID.randomUUID().toString();
            lastLiveDiagnosis = null; savedMeasurementId = null;
            mainHandler.removeCallbacks(diagnosisTimeout); predictionPending = false;
            setDiagnosisStatus("Capturing pulse — awaiting features…");
            clearWaveform();

            updateHeartRate(
                    "-- BPM"
            );

            updateConnectionStatus(
                    "Measurement started"
            );

            return;
        }

        // =====================================================
        // FINAL BPM
        // =====================================================

        if (
                message.startsWith(
                        "BPM_FINAL:"
                )
        ) {

            try {

                String value =
                        message
                                .substring(
                                        "BPM_FINAL:"
                                                .length()
                                )
                                .trim();

                double bpm =
                        Double.parseDouble(
                                value
                        );

                updateHeartRate(
                        String.format(
                                Locale.US,
                                "%.1f BPM",
                                bpm
                        )
                );

            } catch (
                    NumberFormatException e
            ) {

                Log.e(
                        "REAL_TIME_BPM",
                        "Invalid final BPM: "
                                +
                                message
                );
            }

            return;
        }

        // =====================================================
        // MEASUREMENT COMPLETE
        // =====================================================

        if (
                message.equals(
                        "MEASUREMENT_COMPLETE"
                )
        ) {

            updateConnectionStatus(
                    "Measurement complete"
            );
            mainHandler.post(() -> {
                if (lastLiveDiagnosis != null) showLiveDiagnosis(lastLiveDiagnosis);
                else if (predictionPending) setDiagnosisStatus("Capture complete — predicting using phone internet…");
                else if (diagnosisStatus.startsWith("Capturing") || diagnosisStatus.startsWith("Waiting"))
                    setDiagnosisStatus("Capture complete, but no ML features or prediction were received. Upload the corrected ESP32 firmware.");
                // Preserve any HTTP, sensor, or network failure instead of overwriting it with waiting.
            });
        }
    }

    // =========================================================
    // ADD LIVE WAVEFORM POINT
    // =========================================================

    private void recordLiveDiagnosis(org.json.JSONObject response, org.json.JSONObject features) {
        mainHandler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                if (!response.has("prediction") || response.has("error") || !response.optBoolean("success", true)) return;
                String resultId = response.optString("measurementId", measurementId);
                if (!resultId.equals(measurementId)) {
                    response.put("source", "LIVE_SENSOR"); DiagnosisHistoryStore.save(this, response, features); return;
                }
                predictionPending = false; mainHandler.removeCallbacks(diagnosisTimeout);
                lastLiveDiagnosis = new org.json.JSONObject(response.toString());
                lastLiveDiagnosis.put("source", "LIVE_SENSOR");
                lastLiveDiagnosis.put("measurementId", measurementId);
                // Replace the same measurement entry when its final prediction arrives.
                DiagnosisHistoryStore.save(this, lastLiveDiagnosis, features);
                savedMeasurementId = measurementId;
                showLiveDiagnosis(lastLiveDiagnosis);
            } catch (Exception e) { showToast("Could not save diagnosis: " + e.getMessage()); }
        });
    }
    private void showLiveDiagnosis(org.json.JSONObject response) {
        double confidence = response.optDouble("confidence", Double.NaN);
        String text = "Research result: " + response.optString("prediction", "Unavailable")
                + "\nPattern: " + response.optString("pattern_level", "Not assessed")
                + "\nMedical risk: not assessed"
                + (Double.isNaN(confidence) ? "" : "\nConfidence: " + String.format(Locale.US, "%.2f%%", confidence))
                + "\n" + response.optString("recommendation", "") + "\nSaved in Diagnosis history";
        setDiagnosisStatus(text);
    }
    private void setDiagnosisStatus(String text) {
        diagnosisStatus = text;
        TextView view = findViewById(R.id.tvPredictionStatus);
        if (view != null) { view.setTextColor(android.graphics.Color.parseColor("#183344")); view.setText(text); }
    }

    private void addWaveformPoint(
            float waveformValue
    ) {

        mainHandler.post(
                () -> {

                    if (
                            pulseWaveform ==
                                    null
                                    ||
                                    waveformLineData ==
                                            null
                                    ||
                                    waveformDataSet ==
                                            null
                    ) {

                        return;
                    }

                    // =============================================
                    // ADD NEW ENTRY
                    // =============================================

                    waveformLineData.addEntry(
                            new Entry(
                                    waveformX,
                                    waveformValue
                            ),
                            0
                    );

                    waveformX++;

                    // =============================================
                    // LIMIT MEMORY
                    // =============================================

                    while (
                            waveformDataSet
                                    .getEntryCount()
                                    >
                                    MAX_STORED_POINTS
                    ) {

                        waveformDataSet
                                .removeFirst();
                    }

                    // =============================================
                    // NOTIFY CHART
                    // =============================================

                    waveformLineData
                            .notifyDataChanged();

                    pulseWaveform
                            .notifyDataSetChanged();

                    // =============================================
                    // AUTO Y-AXIS
                    // =============================================

                    pulseWaveform
                            .setAutoScaleMinMaxEnabled(
                                    true
                            );

                    pulseWaveform
                            .getAxisLeft()
                            .resetAxisMinimum();

                    pulseWaveform
                            .getAxisLeft()
                            .resetAxisMaximum();

                    // =============================================
                    // VISIBLE WINDOW
                    // =============================================

                    pulseWaveform
                            .setVisibleXRangeMaximum(
                                    MAX_VISIBLE_POINTS
                            );

                    // =============================================
                    // MOVE LIKE A LIVE CRYPTO CHART
                    // =============================================

                    if (
                            waveformX >
                                    MAX_VISIBLE_POINTS
                    ) {

                        pulseWaveform
                                .moveViewToX(
                                        waveformX
                                                -
                                                1
                                );
                    }

                    // =============================================
                    // REDRAW
                    // =============================================

                    pulseWaveform
                            .invalidate();
                }
        );
    }

    // =========================================================
    // CLEAR WAVEFORM
    // =========================================================

    private void clearWaveform() {

        mainHandler.post(
                () -> {

                    waveformX =
                            0f;

                    if (
                            waveformDataSet !=
                                    null
                    ) {

                        waveformDataSet.clear();
                    }

                    if (
                            waveformLineData !=
                                    null
                    ) {

                        waveformLineData
                                .notifyDataChanged();
                    }

                    if (
                            pulseWaveform !=
                                    null
                    ) {

                        pulseWaveform
                                .notifyDataSetChanged();

                        pulseWaveform
                                .fitScreen();

                        pulseWaveform
                                .invalidate();
                    }
                }
        );
    }

    // =========================================================
    // UPDATE BPM
    // =========================================================

    private void updateHeartRate(
            String text
    ) {

        if (guardianEmergency != null) {
            try { guardianEmergency.observeBpm(Double.parseDouble(text.replace("BPM", "").trim())); }
            catch (NumberFormatException e) { guardianEmergency.resetSignal(); }
        }
        mainHandler.post(
                () -> {

                    if (
                            tvHeartRate !=
                                    null
                    ) {

                        tvHeartRate.setText(
                                text
                        );
                    }
                }
        );
    }

    // =========================================================
    // UPDATE STATUS
    // =========================================================

    private void updateConnectionStatus(
            String text
    ) {

        mainHandler.post(
                () -> {

                    if (
                            tvConnectionStatus !=
                                    null
                    ) {

                        tvConnectionStatus.setText(
                                text
                        );
                    }
                }
        );
    }

    // =========================================================
    // TOAST
    // =========================================================

    private void showToast(
            String message
    ) {

        mainHandler.post(
                () ->
                        Toast.makeText(
                                Heart_rate.this,
                                message,
                                Toast.LENGTH_SHORT
                        ).show()
        );
    }

    // =========================================================
    // CLOSE BLUETOOTH
    // =========================================================

    private void closeBluetoothConnection() {

        bluetoothRunning =
                false;

        try {

            if (
                    inputStream !=
                            null
            ) {

                inputStream.close();

                inputStream =
                        null;
            }

        } catch (
                IOException ignored
        ) {
        }

        try {

            if (
                    bluetoothSocket !=
                            null
            ) {

                bluetoothSocket.close();

                bluetoothSocket =
                        null;
            }

        } catch (
                IOException ignored
        ) {
        }
    }

    // =========================================================
    // ON DESTROY
    // =========================================================

    @Override
    protected void onDestroy() {

        bluetoothRunning =
                false;

        closeBluetoothConnection();
        mainHandler.removeCallbacks(diagnosisTimeout);
        if (autoPrediction != null) autoPrediction.close();

        super.onDestroy();
    }
}