package com.example.pulsediagnosis;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Set;

public class Home extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 101;
    private static final int REQUEST_BT_PERMISSION = 102;

    // Bluetooth
    BluetoothAdapter bluetoothAdapter;
    ImageButton bluetoothBtn;
    ViewAnimator viewAnimator;
    ListView listBluetoothDevices;
    ArrayAdapter<String> deviceAdapter;
    ArrayList<String> deviceList;

    // UI
    View btnChannel, btnHeart, btnDiagnosis, view23;
    Button vwProfile, vwSettings, vwLogout, flask;
    ImageButton btnProfile;

    SessionManager session;

    @SuppressLint({"CutPasteId", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // -------------------------
        // Session check
        // -------------------------
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        // -------------------------
        // Initialize UI
        // -------------------------
        android.view.View emergency = findViewById(R.id.btnEmergencyGuardian);
        emergency.setVisibility(View.VISIBLE);
        GuardianEmergencyNotifier emergencyNotifier = new GuardianEmergencyNotifier(this);
        emergency.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Send emergency alert?").setMessage("Notify all accepted guardians that you need assistance.")
                .setPositiveButton("Send alert", (dialog, which) -> emergencyNotifier.manualEmergency())
                .setNegativeButton("Cancel", null).show());
        btnChannel = findViewById(R.id.channel);
        btnHeart = findViewById(R.id.heart);
        btnDiagnosis = findViewById(R.id.diagnosis);
        flask =  findViewById(R.id.flask);

        btnProfile = findViewById(R.id.profile);
        vwProfile = findViewById(R.id.vwProfile);
        vwSettings = findViewById(R.id.vwSettings);
        vwLogout = findViewById(R.id.vwLogout);
        view23 = findViewById(R.id.view23);

        bluetoothBtn = findViewById(R.id.bluetooth);
        viewAnimator = findViewById(R.id.viewAnimator);
        listBluetoothDevices = findViewById(R.id.listBluetoothDevices);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // -------------------------
        // Bluetooth click
        // -------------------------
        bluetoothBtn.setOnClickListener(v -> checkBluetooth());

        // -------------------------
        // Navigation buttons
        // -------------------------
        flask.setOnClickListener(v -> startActivity(new Intent(this, Flask.class)));
        findViewById(R.id.guardianButton).setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Guardian connections")
                .setItems(new String[]{"My accepted guardians", "Incoming / sent requests and emergency alerts", "Search / send guardian request"}, (dialog, which) ->
                    startActivity(new Intent(this, which == 2 ? GuardianSelection.class : GuardianRequests.class).putExtra("showAcceptedGuardians", which == 0))).show());
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("Users").child(session.getUserId()).get()
                .addOnSuccessListener(profile -> {
                    if (Boolean.TRUE.equals(profile.child("guardianEnabled").getValue(Boolean.class)) || "GUARDIAN".equals(profile.child("role").getValue(String.class))) GuardianNotifications.setup(this);
                });

        btnChannel.setOnClickListener(v -> startActivity(new Intent(this, DrChannel.class)));
        btnHeart.setOnClickListener(v ->
                startActivity(new Intent(this, Heart_rate.class)));
        btnDiagnosis.setOnClickListener(v -> startActivity(new Intent(this, Diagnosis.class)));

        // -------------------------
        // Profile menu
        // -------------------------
        btnProfile.setOnClickListener(v -> {
            if (view23.getVisibility() == View.VISIBLE) hideMenu();
            else showMenu();
        });

        vwProfile.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(this, Profile.class));
        });

        vwSettings.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(this, Settings.class));
        });

        vwLogout.setOnClickListener(v -> {
            hideMenu();
            GuardianNotifications.logout(this, () -> {
                FirebaseAuth.getInstance().signOut(); session.logout();
                startActivity(new Intent(this, Login.class)); finish();
            });
        });

        findViewById(R.id.main).setOnClickListener(v -> hideMenu());
    }

    // -------------------------
    // Bluetooth logic
    // -------------------------
    private void checkBluetooth() {

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    },
                    REQUEST_BT_PERMISSION
            );
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            startActivityForResult(
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    REQUEST_ENABLE_BT
            );
        } else {
            showPairedDevices();
        }
    }

    private void showPairedDevices() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        deviceList = new ArrayList<>();

        if (!pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            deviceList.add("No paired Bluetooth devices");
        }

        deviceAdapter = new ArrayAdapter<>(
                this,
                R.layout.item_bluetooth_device,
                deviceList
        );

        listBluetoothDevices.setAdapter(deviceAdapter);

        listBluetoothDevices.setOnItemClickListener((parent, view, position, id) -> {
            String selected = deviceList.get(position);
            Toast.makeText(this, "Selected:\n" + selected, Toast.LENGTH_SHORT).show();

            // NEXT STEP: connect to HC-05 here
        });
    }

    // -------------------------
    // Permission results
    // -------------------------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            showPairedDevices();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BT_PERMISSION &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkBluetooth();
        }
    }

    // -------------------------
    // Menu helpers
    // -------------------------
    private void showMenu() {
        view23.setVisibility(View.VISIBLE);
        vwProfile.setVisibility(View.VISIBLE);
        vwSettings.setVisibility(View.VISIBLE);
        vwLogout.setVisibility(View.VISIBLE);
    }

    private void hideMenu() {
        view23.setVisibility(View.GONE);
        vwProfile.setVisibility(View.GONE);
        vwSettings.setVisibility(View.GONE);
        vwLogout.setVisibility(View.GONE);
    }
    @Override protected void onStart() { super.onStart(); GuardianNotifications.start(this); }
    @Override protected void onStop() { GuardianNotifications.stop(this); super.onStop(); }

}
