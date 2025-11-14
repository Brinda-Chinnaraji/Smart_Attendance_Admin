package com.codecatalyst.smartattendance.smartattendanceadmin;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AddSemesterActivity extends AppCompatActivity {
    private EditText etSemesterName, etStartDate, etEndDate;
    private Button btnSaveSemester;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_semester);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Add Semester");
        }


        db = FirebaseFirestore.getInstance();

        etSemesterName = findViewById(R.id.etSemesterName);
        etStartDate = findViewById(R.id.etStartDate);
        etEndDate = findViewById(R.id.etEndDate);
        btnSaveSemester = findViewById(R.id.btnSaveSemester);

        btnSaveSemester.setOnClickListener(v -> saveSemester());
    }

    private void saveSemester() {
        String name = etSemesterName.getText().toString().trim();
        String startDate = etStartDate.getText().toString().trim();
        String endDate = etEndDate.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(startDate) || TextUtils.isEmpty(endDate)) {
            Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("Name", name);
        data.put("startDate", startDate);
        data.put("EndDate", endDate);

        db.collection("Semester").add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Semester added: " + ref.getId(), Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

}