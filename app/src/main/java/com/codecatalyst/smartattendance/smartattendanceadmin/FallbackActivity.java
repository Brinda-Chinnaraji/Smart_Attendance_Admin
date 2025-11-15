package com.codecatalyst.smartattendance.smartattendanceadmin;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FallbackActivity extends AppCompatActivity {

    private Spinner spinnerEmail, spinnerCourses, spinnerSchedule;
    private Button btnSubmit;
    private String selectedEmail;

    private String selectedCourseName;
    private String selectedSchedule;

    private String selectedCourseId;
    private String selectedScheduleId;


    private String studentId;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private EditText etFirstName;
    private EditText etLastName;
    private List<String> emailList = new ArrayList<>();
    private List<String> courseList = new ArrayList<>();
    private List<String> scheduleList = new ArrayList<>();

    private TextView  tvAttendanceData;
    private String activeSessionUUID;
    private boolean sessionResolved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fallback);

        spinnerEmail = findViewById(R.id.spinnerEmail);
        spinnerCourses = findViewById(R.id.spinnerCourses);
        spinnerSchedule = findViewById(R.id.spinnerSchedule);
        btnSubmit = findViewById(R.id.btnSubmit);
        progressBar = findViewById(R.id.progressBar);
        tvAttendanceData = findViewById(R.id.tvAttendanceData);
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        db = FirebaseFirestore.getInstance();

        // Initially disable submit button
        btnSubmit.setEnabled(false);

        loadEmails();

        btnSubmit.setOnClickListener(v -> submitFallback());

        spinnerEmail.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedEmail = position == 0 ? null : parent.getItemAtPosition(position).toString();
                if (selectedEmail != null) fetchStudentDetails(selectedEmail);
                checkAllSelected();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerCourses.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCourseName = position == 0 ? null : parent.getItemAtPosition(position).toString();
                if (selectedCourseName != null) fetchScheduleForCourse(selectedCourseName);
                checkAllSelected();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerSchedule.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSchedule = position == 0 ? null : parent.getItemAtPosition(position).toString();

                if (selectedCourseName != null && selectedSchedule != null) {
                    // First, get the courseId from the course name
                    db.collection("Courses")
                            .whereEqualTo("CourseName", selectedCourseName)
                            .get()
                            .addOnSuccessListener(courseQuery -> {
                                if (!courseQuery.isEmpty()) {
                                    selectedCourseId = courseQuery.getDocuments().get(0).getId();
                                    fetchActiveSessionForToday(selectedCourseId, selectedSchedule);
                                }
                            });
                }

                checkAllSelected();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

    }

    /**
     * Enables submit button only if all three spinners have a valid selection
     */
    private void checkAllSelected() {
        boolean allSelected = selectedEmail != null
                && selectedCourseName != null
                && spinnerSchedule.getSelectedItemPosition() != 0;
        btnSubmit.setEnabled(allSelected);
    }

    private void fetchStudentDetails(String email) {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("student")
                .whereEqualTo("Email", email)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    progressBar.setVisibility(View.GONE);

                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        studentId = doc.getId();
                        String firstName = doc.getString("FirstName");
                        String lastName = doc.getString("LastName");

                        etFirstName.setText(firstName);
                        etLastName.setText(lastName);

                        // You can now use this info to fetch scheduled courses for this student
                        fetchScheduledCoursesForStudent(studentId);

                    } else {
                        Toast.makeText(this, "No student found with this email", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error fetching student: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // Load emails from student collection
    private void loadEmails() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("student")
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        emailList.clear();
                        emailList.add("Select Email");
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            String email = doc.getString("Email");
                            if (email != null) emailList.add(email);
                        }
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                                android.R.layout.simple_spinner_dropdown_item, emailList);
                        spinnerEmail.setAdapter(adapter);

                    } else {
                        Toast.makeText(this, "Failed to load emails", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Load courses from Firestore
    private void fetchScheduledCoursesForStudent(String studentId) {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("Courses")
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        courseList.clear();
                        courseList.add("Enrolled Courses"); // default option

                        for (QueryDocumentSnapshot courseDoc : task.getResult()) {
                            String courseId = courseDoc.getId();
                            String courseName = courseDoc.getString("CourseName");

                            // Go to schedule subcollection
                            db.collection("Courses")
                                    .document(courseId)
                                    .collection("Schedule")
                                    .get()
                                    .addOnSuccessListener(scheduleQuery -> {
                                        for (QueryDocumentSnapshot scheduleDoc : scheduleQuery) {
                                            List<String> enrolledList = (List<String>) scheduleDoc.get("StudentsEnrolled");

                                            if (enrolledList != null && enrolledList.contains(studentId)) {
                                                // Add course if not already added
                                                if (courseName != null && !courseList.contains(courseName)) {
                                                    courseList.add(courseName);
                                                }
                                            }
                                        }

                                        // Update spinner adapter
                                        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                                this,
                                                android.R.layout.simple_spinner_dropdown_item,
                                                courseList
                                        );
                                        spinnerCourses.setAdapter(adapter);
                                    });
                        }

                    } else {
                        Toast.makeText(this,
                                "Failed to fetch courses: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchScheduleForCourse(String selectedCourseName) {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("Courses")
                .whereEqualTo("CourseName", selectedCourseName)
                .get()
                .addOnSuccessListener(courseQuery -> {
                    if (!courseQuery.isEmpty()) {

                        DocumentSnapshot courseDoc = courseQuery.getDocuments().get(0);
                        selectedCourseId = courseDoc.getId();  // SAVE COURSE ID

                        db.collection("Courses")
                                .document(selectedCourseId)
                                .collection("Schedule")
                                .get()
                                .addOnSuccessListener(scheduleQuery -> {

                                    scheduleList.clear();
                                    scheduleList.add("Select Schedule");

                                    final List<String> scheduleIdList = new ArrayList<>();
                                    scheduleIdList.add("NONE"); // index 0 placeholder

                                    for (QueryDocumentSnapshot scheduleDoc : scheduleQuery) {
                                        String day = scheduleDoc.getString("Day");
                                        String scheduleId = scheduleDoc.getId();
                                        if (day != null) {
                                            scheduleList.add(day);
                                            scheduleIdList.add(scheduleDoc.getId()); // SAVE SCHEDULE ID
                                        }
                                    }

                                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                            this,
                                            android.R.layout.simple_spinner_dropdown_item,
                                            scheduleList
                                    );

                                    spinnerSchedule.setAdapter(adapter);

                                    // When schedule selected → store scheduleId
                                    spinnerSchedule.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                        @Override
                                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                            if (position > 0) {
                                                selectedSchedule = scheduleList.get(position);
                                                selectedScheduleId = scheduleIdList.get(position); // IMPORTANT
                                                fetchActiveSessionForToday(selectedCourseId, selectedScheduleId);
                                            }
                                            String selectedItem = spinnerSchedule.getSelectedItem().toString();

                                            if (selectedItem.contains("___")) {
                                                String[] parts = selectedItem.split("___");
                                                selectedSchedule = parts[0];        // DAY name
                                                selectedScheduleId = parts[1];      // REAL scheduleId
                                            }
                                            checkAllSelected();
                                        }

                                        @Override
                                        public void onNothingSelected(AdapterView<?> parent) {}
                                    });

                                    progressBar.setVisibility(View.GONE);
                                });
                    }
                });
    }


    // Call this after a schedule is selected
    private void fetchActiveSessionForToday(String courseId, String scheduleId) {
        progressBar.setVisibility(View.VISIBLE);
        sessionResolved = false;
        activeSessionUUID = null;

        String today = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? LocalDate.now().toString()
                : new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());

        DocumentReference todayDocRef = db.collection("Courses")
                .document(courseId)
                .collection("Schedule")
                .document(scheduleId)
                .collection("Attendance")
                .document(today);

        tvAttendanceData.setText("Checking session for today...");

        todayDocRef.get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBar.setVisibility(View.GONE);

                    if (!documentSnapshot.exists()) {
                        tvAttendanceData.setText("❌ No attendance document for today.");
                        return;
                    }

                    Map<String, Object> attendanceData = documentSnapshot.getData();
                    if (attendanceData == null) {
                        tvAttendanceData.setText("❌ Attendance empty for today.");
                        return;
                    }

                    for (Map.Entry<String, Object> entry : attendanceData.entrySet()) {
                        if (entry.getValue() instanceof Map) {
                            Map<String, Object> session = (Map<String, Object>) entry.getValue();
                            String status = (String) session.get("Status");

                            if ("Active".equals(status)) {
                                activeSessionUUID = entry.getKey(); // SESSION ID
                                sessionResolved = true;
                                tvAttendanceData.setText("✅ Active session: " + activeSessionUUID);
                                return;
                            }
                        }
                    }

                    tvAttendanceData.setText("❌ No active session found today.");
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    tvAttendanceData.setText("❌ Error: " + e.getMessage());
                });
    }


    // Submit fallback to Firestore
    private void submitFallback() {
        if (activeSessionUUID == null) {
            Toast.makeText(this, "No active session today!", Toast.LENGTH_SHORT).show();
            return;
        }

        String today = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? LocalDate.now().toString()
                : "unknown";

        // Firestore path:
        DocumentReference attendanceDoc = db.collection("Courses")
                .document(selectedCourseId)
                .collection("Schedule")
                .document(selectedScheduleId)
                .collection("Attendance")
                .document(today);

        // Student attendance data
        Map<String, Object> studentInfo = new HashMap<>();
        studentInfo.put("Status", "Present");
        studentInfo.put("timestamp", Timestamp.now());

        // Build nested structure:
        // {
        //   activeSessionUUID: {
        //     "StudentAttendanceData": {
        //         studentId: { Status, timestamp }
        //     }
        //   }
        // }

        Map<String, Object> studentMap = new HashMap<>();
        studentMap.put(studentId, studentInfo);

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("StudentAttendanceData", studentMap);

        Map<String, Object> rootMap = new HashMap<>();
        rootMap.put(activeSessionUUID, sessionData);

        attendanceDoc.set(rootMap, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Attendance saved correctly for " + studentId);
                  //  Toast.makeText(this, "✅ Attendance saved correctly for", Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(false);
                    Intent intent = new Intent(FallbackActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save: " + e.getMessage());
                });
    }


}
