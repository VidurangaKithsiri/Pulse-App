package com.example.pulsediagnosis;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class EditProfileActivity extends AppCompatActivity {

    EditText edtName, edtAge, edtLang, edtContact, edtAbout,editNo;
    Group patientGroup, doctorGroup;
    ImageView img;
    Button btnSave, btnUpload;
    Uri imageUri;
    SessionManager session;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edite_profile);

        session = new SessionManager(this);

        img = findViewById(R.id.imagePreview);
        edtName = findViewById(R.id.edtName);
        edtAge = findViewById(R.id.edtAge);
        edtLang = findViewById(R.id.edtLang);
        edtContact = findViewById(R.id.edtContact);
        edtAbout = findViewById(R.id.edtAbout);
        btnSave = findViewById(R.id.btnSave);
        btnUpload = findViewById(R.id.btnUpload);
        editNo = findViewById(R.id.editNo);

        patientGroup = findViewById(R.id.patientGroup);
        doctorGroup = findViewById(R.id.doctorGroup);

        if ("PATIENT".equals(session.getUserType())) {
            doctorGroup.setVisibility(View.GONE);

        } else if ("DOCTOR".equals(session.getUserType())) {
            patientGroup.setVisibility(View.GONE);

        } else {
            Toast.makeText(this,
                    "Session error: user type missing",
                    Toast.LENGTH_SHORT).show();
        }

        btnUpload.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setType("image/*");
            startActivityForResult(i, 100);
        });

        btnSave.setOnClickListener(v -> saveProfile());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
            img.setImageURI(imageUri);
            uploadImage();
        }
    }

    private void uploadImage() {
        StorageReference ref = FirebaseStorage.getInstance()
                .getReference("profiles")
                .child(session.getUserId());

        ref.putFile(imageUri)
                .addOnSuccessListener(t ->
                        ref.getDownloadUrl().addOnSuccessListener(uri -> {

                            String node = "PATIENT".equals(session.getUserType())
                                    ? "Users" : "Doctors";

                            FirebaseDatabase.getInstance()
                                    .getReference(node)
                                    .child(session.getUserId())
                                    .child("profileImage")
                                    .setValue(uri.toString());
                        }));
    }

    private void saveProfile() {

        String uid = session.getUserId();

        if ("PATIENT".equals(session.getUserType())) {

            FirebaseDatabase.getInstance().getReference("Users").child(uid)
                    .child("name").setValue(edtName.getText().toString());
            FirebaseDatabase.getInstance().getReference("Users").child(uid)
                    .child("contact").setValue(editNo.getText().toString());
            FirebaseDatabase.getInstance().getReference("Users").child(uid)
                    .child("age").setValue(edtAge.getText().toString());
            FirebaseDatabase.getInstance().getReference("Users").child(uid)
                    .child("language").setValue(edtLang.getText().toString());

        } else {

            FirebaseDatabase.getInstance().getReference("Doctors").child(uid)
                    .child("name").setValue(edtName.getText().toString());
            FirebaseDatabase.getInstance().getReference("Doctors").child(uid)
                    .child("contact").setValue(edtContact.getText().toString());
            FirebaseDatabase.getInstance().getReference("Doctors").child(uid)
                    .child("about").setValue(edtAbout.getText().toString());
        }

        Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
        finish();
    }
}
