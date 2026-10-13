package com.example.pulsediagnosis;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class DrChannel extends AppCompatActivity {

    EditText etName, etContact, etAge;
    Button btnSubmit;

    RecyclerView recyclerView;
    ArrayList<Doctor> doctorList;
    DoctorAdapter adapter;

    DatabaseReference doctorRef, patientRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dr_channel);

        // RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        doctorList = new ArrayList<>();
        adapter = new DoctorAdapter(this, doctorList);
        recyclerView.setAdapter(adapter);

        doctorRef = FirebaseDatabase.getInstance().getReference("Doctors");

        doctorRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                doctorList.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    Doctor doctor = snap.getValue(Doctor.class);
                    doctorList.add(doctor);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(DrChannel.this,
                        error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Patient Form
        etName = findViewById(R.id.PtName);
        etContact = findViewById(R.id.PtContact);
        etAge = findViewById(R.id.PtAge);
        btnSubmit = findViewById(R.id.btnSubmit);

        patientRef = FirebaseDatabase.getInstance().getReference("Users");

        btnSubmit.setOnClickListener(v -> {

            String name = etName.getText().toString().trim();
            String contact = etContact.getText().toString().trim();
            String age = etAge.getText().toString().trim();

            if (name.isEmpty() || contact.isEmpty() || age.isEmpty()) {
                Toast.makeText(this,
                        "Please fill all fields",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String patientId = patientRef.push().getKey();
            Patient patient = new Patient(name, contact, age);

            patientRef.child(patientId).setValue(patient)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this,
                                "Patient saved successfully",
                                Toast.LENGTH_SHORT).show();

                        etName.setText("");
                        etContact.setText("");
                        etAge.setText("");
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
        });
    }
}
