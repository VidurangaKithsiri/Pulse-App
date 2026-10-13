package com.example.pulsediagnosis;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MedicineScheduleActivity extends AppCompatActivity {
    private EditText medicineName, dosage;
    private Button timeButton, saveButton;
    private String selectedTime;
    private SessionManager session;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_schedule);
        session = new SessionManager(this);
        medicineName = findViewById(R.id.medicineName);
        dosage = findViewById(R.id.medicineDosage);
        timeButton = findViewById(R.id.medicineTime);
        saveButton = findViewById(R.id.saveMedicine);
        timeButton.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> {
            selectedTime = String.format(Locale.US, "%02d:%02d", hour, minute);
            timeButton.setText(selectedTime);
        }, 8, 0, true).show());
        saveButton.setOnClickListener(v -> saveMedicineSchedule(
                medicineName.getText().toString().trim(),
                dosage.getText().toString().trim(), selectedTime));
    }

    private void saveMedicineSchedule(String name, String dose, String time) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String patientId = session.getUserId();
        if (user == null || patientId == null || !patientId.equals(user.getUid()) ||
                !"PATIENT".equals(session.getUserType())) {
            Toast.makeText(this, "Please sign in as a patient", Toast.LENGTH_LONG).show();
            return;
        }
        if (name.isEmpty() || dose.isEmpty() || time == null) {
            Toast.makeText(this, "Enter medicine, dosage and time", Toast.LENGTH_SHORT).show();
            return;
        }
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("MedicineSchedules")
                .child(patientId).push();
        String id = ref.getKey();
        if (id == null) return;
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("medicineId", id);
        schedule.put("medicineName", name);
        schedule.put("dosage", dose);
        schedule.put("time", time);
        schedule.put("enabled", true);
        saveButton.setEnabled(false);
        ref.setValue(schedule).addOnCompleteListener(task -> {
            saveButton.setEnabled(true);
            if (task.isSuccessful()) {
                Toast.makeText(this, "Medicine schedule saved", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Save failed: " + (task.getException() == null ? "Unknown error" :
                        task.getException().getMessage()), Toast.LENGTH_LONG).show();
            }
        });
    }
}
