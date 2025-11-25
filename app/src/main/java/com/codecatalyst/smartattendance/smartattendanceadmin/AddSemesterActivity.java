package com.codecatalyst.smartattendance.smartattendanceadmin;

import android.app.DatePickerDialog; // ADDED: For the date picker
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView; // ADDED: For the clickable date fields
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar; // ADDED: To get current date for the picker
import java.util.HashMap;
import java.util.Locale; // ADDED: For date formatting
import java.util.Map;
public class AddSemesterActivity extends AppCompatActivity {
    // MODIFIED: Changed etStartDate and etEndDate to TextView
    private EditText etSemesterName;
    private TextView etStartDate, etEndDate;
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
        // MODIFIED: Initializing TextViews for date fields
        etStartDate = findViewById(R.id.etStartDate);
        etEndDate = findViewById(R.id.etEndDate);
        btnSaveSemester = findViewById(R.id.btnSaveSemester);

        // ADDED: Set click listeners to show the date picker dialog
        etStartDate.setOnClickListener(v -> showDatePickerDialog(etStartDate));
        etEndDate.setOnClickListener(v -> showDatePickerDialog(etEndDate));

        btnSaveSemester.setOnClickListener(v -> saveSemester());
    }

    // ADDED: New method to show the DatePickerDialog
    private void showDatePickerDialog(TextView dateView) {
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    // Set the date on the TextView in YYYY-MM-DD format
                    String selectedDate = String.format(Locale.getDefault(), "%d-%02d-%02d", year1, monthOfYear + 1, dayOfMonth);
                    dateView.setText(selectedDate);
                }, year, month, day);
        datePickerDialog.show();
    }

    private void saveSemester() {
        String name = etSemesterName.getText().toString().trim();
        // NOTE: .getText() works on TextViews as well, so no change is needed here
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