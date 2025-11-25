package com.codecatalyst.smartattendance.smartattendanceadmin;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddCourseActivity extends AppCompatActivity {

    // MODIFIED: Replaced etDay with spinnerDay
    // private EditText etCourseName, etShortCode;
    private EditText etCourseName, etShortCode;

    private TextView etStartTime, etEndTime;
    private Spinner spinnerDay;
    private Button btnPickProfessor, btnQuickNewProfessor, btnPickSemester, btnPickStudents, btnSaveCourse;
    private TextView tvPickedProfessor, tvPickedSemester, tvPickedStudents;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String selectedProfessorId = null;
    private String selectedSemesterId = null;
    private final List<String> selectedStudentIds = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_course);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Add Course");
        }

        etCourseName = findViewById(R.id.etCourseName);
        etShortCode = findViewById(R.id.etShortCode);
        // MODIFIED: Initialize Spinner instead of EditText
        spinnerDay = findViewById(R.id.spinnerDay);
        etStartTime = findViewById(R.id.etStartTime);
        etEndTime = findViewById(R.id.etEndTime);

        btnPickProfessor = findViewById(R.id.btnPickProfessor);
        btnQuickNewProfessor = findViewById(R.id.btnQuickNewProfessor);
        btnPickSemester = findViewById(R.id.btnPickSemester);
        btnPickStudents = findViewById(R.id.btnPickStudents);
        btnSaveCourse = findViewById(R.id.btnSaveCourse);

        tvPickedProfessor = findViewById(R.id.tvPickedProfessor);
        tvPickedSemester = findViewById(R.id.tvPickedSemester);
        tvPickedStudents = findViewById(R.id.tvPickedStudents);

        // ADDED: Populate the day spinner
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, days);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDay.setAdapter(adapter);

        btnPickProfessor.setOnClickListener(v -> pickProfessor());
        btnQuickNewProfessor.setOnClickListener(v -> quickCreateProfessor());
        btnPickSemester.setOnClickListener(v -> pickSemester());
        btnPickStudents.setOnClickListener(v -> pickStudents());
        btnSaveCourse.setOnClickListener(v -> saveCourse());

        etStartTime.setOnClickListener(v -> showTimePicker(etStartTime));
        etEndTime.setOnClickListener(v -> showTimePicker(etEndTime));
    }

    private void showTimePicker(TextView timeView) {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {
            String amPm;
            int hourIn12Format;

            if (hourOfDay >= 12) {
                amPm = "PM";
                hourIn12Format = (hourOfDay == 12) ? 12 : hourOfDay - 12;
            } else {
                amPm = "AM";
                hourIn12Format = (hourOfDay == 0) ? 12 : hourOfDay;
            }

            String time = String.format(Locale.getDefault(), "%02d:%02d %s", hourIn12Format, minuteOfHour, amPm);
            timeView.setText(time);
        }, hour, minute, false);

        timePickerDialog.show();
    }


    private void pickProfessor() {
        db.collection("Professor").get().addOnSuccessListener(qs -> {
            List<String> names = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (DocumentSnapshot d : qs) {
                names.add(d.getString("Name"));
                ids.add(d.getId());
            }
            String[] items = names.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("Select Professor")
                    .setItems(items, (dialog, which) -> {
                        selectedProfessorId = ids.get(which);
                        tvPickedProfessor.setText("Professor: " + items[which] + " (" + selectedProfessorId + ")");
                    })
                    .show();
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Load professors failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void quickCreateProfessor() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 20, 40, 0);

        EditText etName = new EditText(this); etName.setHint("Name");
        EditText etEmail = new EditText(this); etEmail.setHint("Email"); etEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText etPassword = new EditText(this); etPassword.setHint("Password"); etPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText etDept = new EditText(this); etDept.setHint("Department");
        EditText etCode = new EditText(this); etCode.setHint("Professor Code");

        container.addView(etName);
        container.addView(etEmail);
        container.addView(etPassword);
        container.addView(etDept);
        container.addView(etCode);

        new AlertDialog.Builder(this)
                .setTitle("Quick New Professor")
                .setView(container)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String email = etEmail.getText().toString().trim();
                    String pass = etPassword.getText().toString().trim();
                    String dept = etDept.getText().toString().trim();
                    String code = etCode.getText().toString().trim();
                    if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                        Toast.makeText(this, "Name, email, password required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DocumentReference pRef = db.collection("Professor").document();
                    Map<String, Object> prof = new HashMap<>();
                    prof.put("Name", name);
                    prof.put("email", email);
                    prof.put("password", pass);
                    prof.put("department", dept);
                    prof.put("professorCode", code);
                    prof.put("coursesTaught", new ArrayList<String>());

                    pRef.set(prof).addOnSuccessListener(unused -> {
                        selectedProfessorId = pRef.getId();
                        tvPickedProfessor.setText("Professor: " + name + " (" + selectedProfessorId + ")");
                    }).addOnFailureListener(e ->
                            Toast.makeText(this, "Create failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickSemester() {
        db.collection("Semester").get().addOnSuccessListener(qs -> {
            List<String> names = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (DocumentSnapshot d : qs) {
                names.add(d.getString("Name"));
                ids.add(d.getId());
            }
            String[] items = names.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("Select Semester")
                    .setItems(items, (dialog, which) -> {
                        selectedSemesterId = ids.get(which);
                        tvPickedSemester.setText("Semester: " + items[which] + " (" + selectedSemesterId + ")");
                    })
                    .show();
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Load semesters failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void pickStudents() {
        db.collection("student").get().addOnSuccessListener(qs -> {
            List<String> labels = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (DocumentSnapshot d : qs) {
                String fn = d.getString("FirstName");
                String ln = d.getString("LastName");
                labels.add((fn != null ? fn : "") + " " + (ln != null ? ln : ""));
                ids.add(d.getId());
            }
            boolean[] checked = new boolean[ids.size()];
            String[] items = labels.toArray(new String[0]);

            new AlertDialog.Builder(this)
                    .setTitle("Select Students")
                    .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                        String id = ids.get(which);
                        if (isChecked && !selectedStudentIds.contains(id)) selectedStudentIds.add(id);
                        if (!isChecked) selectedStudentIds.remove(id);
                    })
                    .setPositiveButton("Done", (d, w) ->
                            tvPickedStudents.setText("Students: " + selectedStudentIds.size() + " selected"))
                    .show();
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Load students failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void saveCourse() {
        String courseName = etCourseName.getText().toString().trim();
        String shortCode = etShortCode.getText().toString().trim();
        // MODIFIED: Get selected day from Spinner
        String day = spinnerDay.getSelectedItem().toString();
        String startTime = etStartTime.getText().toString().trim();
        String endTime = etEndTime.getText().toString().trim();

        if (TextUtils.isEmpty(courseName) || TextUtils.isEmpty(shortCode)) {
            Toast.makeText(this, "Course name and short code required.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedProfessorId == null) {
            Toast.makeText(this, "Pick or create a Professor.", Toast.LENGTH_SHORT).show();
            return;
        }
        // MODIFIED: Check for a valid day selection
        if (TextUtils.isEmpty(day) || TextUtils.isEmpty(startTime) || TextUtils.isEmpty(endTime) || selectedSemesterId == null) {
            Toast.makeText(this, "Fill schedule fields and pick semester.", Toast.LENGTH_SHORT).show();
            return;
        }

        WriteBatch batch = db.batch();

        DocumentReference courseRef = db.collection("Courses").document();
        Map<String, Object> course = new HashMap<>();
        course.put("CourseName", courseName);
        course.put("ShortCode", shortCode);
        course.put("ProfessorID", selectedProfessorId);
        batch.set(courseRef, course);

        DocumentReference profRef = db.collection("Professor").document(selectedProfessorId);
        batch.update(profRef, "coursesTaught", FieldValue.arrayUnion(courseRef.getId()));

        DocumentReference schedRef = courseRef.collection("Schedule").document();
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("Day", day);
        schedule.put("StartTime", startTime);
        schedule.put("EndTime", endTime);
        schedule.put("Semester", selectedSemesterId);
        schedule.put("StudentsEnrolled", new ArrayList<>(selectedStudentIds));
        batch.set(schedRef, schedule);

        batch.commit().addOnSuccessListener(unused -> {
            String newCourseId = courseRef.getId();
            Toast.makeText(this, "Course saved: " + newCourseId, Toast.LENGTH_LONG).show();
            getIntent().putExtra("NEW_COURSE_ID", newCourseId);
            getIntent().putExtra("PROFESSOR_ID", selectedProfessorId);
            setResult(RESULT_OK, getIntent());
            finish();
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

}