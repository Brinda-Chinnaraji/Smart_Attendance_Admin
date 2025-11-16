package com.codecatalyst.smartattendance.smartattendanceadmin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProfessorAttendanceHistoryActivity extends AppCompatActivity {

    private static final String TAG = "ProfHistory";

    private FirebaseFirestore db;
    private String professorId;

    // UI
    private ImageButton btnBackHistoryProf;
    private Spinner spSemester, spCourse, spSchedule, spDate, spSession;
    private LinearLayout cardCourse, cardSchedule, cardDate, cardSession, cardHistory;
    private RecyclerView rvHistory;
    private ProgressBar progressHistory;
    private TextView tvNoData, tvHistoryHeader;

    // Semester
    private final List<String> semesterNames = new ArrayList<>();
    private final List<String> semesterIds = new ArrayList<>();
    private String selectedSemesterId;

    // Courses (for this professor & semester)
    private final List<String> courseNames = new ArrayList<>();
    private final List<String> courseIds = new ArrayList<>();
    private String selectedCourseId;

    // Schedules
    private final List<String> scheduleNames = new ArrayList<>();
    private final List<String> scheduleIds = new ArrayList<>();
    private String selectedScheduleId;

    // Dates
    private final List<String> dateLabels = new ArrayList<>();
    private String selectedDate;

    // Sessions (per date)
    private final List<String> sessionLabels = new ArrayList<>();
    private final List<String> sessionIds = new ArrayList<>();
    private String selectedSessionUUID;

    // History data
    private final List<ProfAttendanceRow> historyRows = new ArrayList<>();
    private ProfHistoryAdapter historyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_professor_attendance_history);

        db = FirebaseFirestore.getInstance();

        Intent intent = getIntent();
        professorId = intent.getStringExtra("PROFESSOR_ID");

        // Bind views
        btnBackHistoryProf = findViewById(R.id.btnBackHistoryProf);
        spSemester = findViewById(R.id.spSemester);
        spCourse = findViewById(R.id.spCourse);
        spSchedule = findViewById(R.id.spSchedule);
        spDate = findViewById(R.id.spDate);
        spSession = findViewById(R.id.spSession);

        cardCourse = findViewById(R.id.cardCourse);
        cardSchedule = findViewById(R.id.cardSchedule);
        cardDate = findViewById(R.id.cardDate);
        cardSession = findViewById(R.id.cardSession);
        cardHistory = findViewById(R.id.cardHistory);

        rvHistory = findViewById(R.id.rvAttendanceHistory);
        progressHistory = findViewById(R.id.progressHistory);
        tvNoData = findViewById(R.id.tvNoData);
        tvHistoryHeader = findViewById(R.id.tvHistoryHeader);

        cardCourse.setVisibility(View.GONE);
        cardSchedule.setVisibility(View.GONE);
        cardDate.setVisibility(View.GONE);
        cardSession.setVisibility(View.GONE);
        cardHistory.setVisibility(View.GONE);

        btnBackHistoryProf.setOnClickListener(v -> finish());

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new ProfHistoryAdapter(historyRows);
        rvHistory.setAdapter(historyAdapter);

        setupSemesterSpinner();
    }

    // ---------------------- SEMESTER ----------------------
    private void setupSemesterSpinner() {
        semesterNames.clear();
        semesterIds.clear();

        semesterNames.add("Select semester");
        semesterIds.add(null);

        db.collection("Semester")
                .get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String id = doc.getId();
                        String name = doc.getString("Name");
                        if (name == null || name.isEmpty()) {
                            name = id;
                        }
                        semesterIds.add(id);
                        semesterNames.add(name);
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            semesterNames
                    );
                    adapter.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item);
                    spSemester.setAdapter(adapter);
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to load semesters: " + e.getMessage()));

        spSemester.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id
            ) {
                if (position == 0) {
                    selectedSemesterId = null;
                    cardCourse.setVisibility(View.GONE);
                    cardSchedule.setVisibility(View.GONE);
                    cardDate.setVisibility(View.GONE);
                    cardSession.setVisibility(View.GONE);
                    cardHistory.setVisibility(View.GONE);
                    return;
                }

                selectedSemesterId = semesterIds.get(position);
                loadCoursesForProfessorAndSemester(selectedSemesterId);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // ---------------------- COURSES ----------------------
    private void loadCoursesForProfessorAndSemester(String semesterId) {
        cardCourse.setVisibility(View.GONE);
        cardSchedule.setVisibility(View.GONE);
        cardDate.setVisibility(View.GONE);
        cardSession.setVisibility(View.GONE);
        cardHistory.setVisibility(View.GONE);
        tvNoData.setVisibility(View.GONE);

        courseNames.clear();
        courseIds.clear();

        courseNames.add("Select course");
        courseIds.add(null);

        // Get professor's courses
        db.collection("Professor").document(professorId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        updateCourseSpinner();
                        return;
                    }

                    List<String> profCourses =
                            (List<String>) doc.get("coursesTaught");
                    if (profCourses == null || profCourses.isEmpty()) {
                        updateCourseSpinner();
                        return;
                    }

                    final int[] pending = {profCourses.size()};

                    for (String courseId : profCourses) {
                        db.collection("Courses").document(courseId)
                                .get()
                                .addOnSuccessListener(courseDoc -> {
                                    if (courseDoc.exists()) {
                                        String courseName =
                                                courseDoc.getString("CourseName");
                                        if (courseName == null || courseName.isEmpty()) {
                                            courseName = courseId;
                                        }

                                        // Check if this course has schedule(s) in this semester
                                        String finalCourseName = courseName;
                                        courseDoc.getReference()
                                                .collection("Schedule")
                                                .whereEqualTo("Semester", semesterId)
                                                .get()
                                                .addOnSuccessListener(scheduleSnap -> {
                                                    if (!scheduleSnap.isEmpty()) {
                                                        if (!courseIds.contains(courseId)) {
                                                            courseIds.add(courseId);
                                                            courseNames.add(finalCourseName);
                                                        }
                                                    }
                                                    pending[0]--;
                                                    if (pending[0] <= 0) {
                                                        updateCourseSpinner();
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG,
                                                            "Schedule query failed: " + e.getMessage());
                                                    pending[0]--;
                                                    if (pending[0] <= 0) {
                                                        updateCourseSpinner();
                                                    }
                                                });
                                    } else {
                                        pending[0]--;
                                        if (pending[0] <= 0) {
                                            updateCourseSpinner();
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Course query failed: " + e.getMessage());
                                    pending[0]--;
                                    if (pending[0] <= 0) {
                                        updateCourseSpinner();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Professor query failed: " + e.getMessage()));
    }

    private void updateCourseSpinner() {
        if (courseIds.size() <= 1) {
            cardCourse.setVisibility(View.GONE);
            cardSchedule.setVisibility(View.GONE);
            cardDate.setVisibility(View.GONE);
            cardSession.setVisibility(View.GONE);
            cardHistory.setVisibility(View.GONE);
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                courseNames
        );
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spCourse.setAdapter(adapter);
        cardCourse.setVisibility(View.VISIBLE);

        spCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id
            ) {
                if (position == 0) {
                    selectedCourseId = null;
                    cardSchedule.setVisibility(View.GONE);
                    cardDate.setVisibility(View.GONE);
                    cardSession.setVisibility(View.GONE);
                    cardHistory.setVisibility(View.GONE);
                    return;
                }

                selectedCourseId = courseIds.get(position);
                loadSchedulesForCourse(selectedCourseId, selectedSemesterId);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // ---------------------- SCHEDULES ----------------------
    private void loadSchedulesForCourse(String courseId, String semesterId) {
        cardSchedule.setVisibility(View.GONE);
        cardDate.setVisibility(View.GONE);
        cardSession.setVisibility(View.GONE);
        cardHistory.setVisibility(View.GONE);
        tvNoData.setVisibility(View.GONE);

        scheduleNames.clear();
        scheduleIds.clear();
        scheduleNames.add("Select schedule");
        scheduleIds.add(null);

        db.collection("Courses").document(courseId)
                .collection("Schedule")
                .whereEqualTo("Semester", semesterId)
                .get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        String schedId = doc.getId();
                        String day = doc.getString("Day");
                        String startTime = doc.getString("StartTime");
                        String endTime = doc.getString("EndTime");
                        String label = (day == null ? "" : day) +
                                " " +
                                (startTime == null ? "" : startTime) +
                                " - " +
                                (endTime == null ? "" : endTime);

                        scheduleIds.add(schedId);
                        scheduleNames.add(label.trim());
                    }

                    if (scheduleIds.size() <= 1) {
                        cardSchedule.setVisibility(View.GONE);
                        cardDate.setVisibility(View.GONE);
                        cardSession.setVisibility(View.GONE);
                        cardHistory.setVisibility(View.GONE);
                        return;
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            scheduleNames
                    );
                    adapter.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item);
                    spSchedule.setAdapter(adapter);
                    cardSchedule.setVisibility(View.VISIBLE);

                    spSchedule.setOnItemSelectedListener(
                            new AdapterView.OnItemSelectedListener() {
                                @Override
                                public void onItemSelected(
                                        AdapterView<?> parent,
                                        View view,
                                        int position,
                                        long id
                                ) {
                                    if (position == 0) {
                                        selectedScheduleId = null;
                                        cardDate.setVisibility(View.GONE);
                                        cardSession.setVisibility(View.GONE);
                                        cardHistory.setVisibility(View.GONE);
                                        return;
                                    }

                                    selectedScheduleId = scheduleIds.get(position);
                                    loadDatesForSchedule(selectedCourseId, selectedScheduleId);
                                }

                                @Override
                                public void onNothingSelected(
                                        AdapterView<?> parent
                                ) {}
                            });
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Schedules load failed: " + e.getMessage()));
    }

    // ---------------------- DATES ----------------------
    private void loadDatesForSchedule(String courseId, String scheduleId) {
        cardDate.setVisibility(View.GONE);
        cardSession.setVisibility(View.GONE);
        cardHistory.setVisibility(View.GONE);
        tvNoData.setVisibility(View.GONE);

        dateLabels.clear();
        dateLabels.add("Select date");

        db.collection("Courses").document(courseId)
                .collection("Schedule").document(scheduleId)
                .collection("Attendance")
                .get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        String dateId = doc.getId(); // e.g. 2025-11-15
                        dateLabels.add(dateId);
                    }

                    if (dateLabels.size() <= 1) {
                        cardDate.setVisibility(View.GONE);
                        cardSession.setVisibility(View.GONE);
                        cardHistory.setVisibility(View.GONE);
                        return;
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            dateLabels
                    );
                    adapter.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item);
                    spDate.setAdapter(adapter);
                    cardDate.setVisibility(View.VISIBLE);

                    spDate.setOnItemSelectedListener(
                            new AdapterView.OnItemSelectedListener() {
                                @Override
                                public void onItemSelected(
                                        AdapterView<?> parent,
                                        View view,
                                        int position,
                                        long id
                                ) {
                                    if (position == 0) {
                                        selectedDate = null;
                                        cardSession.setVisibility(View.GONE);
                                        cardHistory.setVisibility(View.GONE);
                                        return;
                                    }

                                    selectedDate = dateLabels.get(position);
                                    // Now load sessions for this date
                                    loadSessionsForDay(selectedCourseId, selectedScheduleId, selectedDate);
                                }

                                @Override
                                public void onNothingSelected(
                                        AdapterView<?> parent
                                ) {}
                            });
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Dates load failed: " + e.getMessage()));
    }

    // ---------------------- SESSIONS PER DATE ----------------------
    private void loadSessionsForDay(String courseId, String scheduleId, String date) {
        cardSession.setVisibility(View.GONE);
        cardHistory.setVisibility(View.GONE);
        selectedSessionUUID = null;

        sessionLabels.clear();
        sessionIds.clear();

        sessionLabels.add("Select session");
        sessionIds.add(null);

        db.collection("Courses").document(courseId)
                .collection("Schedule").document(scheduleId)
                .collection("Attendance").document(date)
                .get()
                .addOnSuccessListener(doc -> {
                    Map<String, Object> data = doc.getData();
                    if (data == null || data.isEmpty()) {
                        return;
                    }

                    SimpleDateFormat timeFormat =
                            new SimpleDateFormat("hh:mm a", Locale.getDefault());

                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        String uuidKey = entry.getKey();
                        Object val = entry.getValue();
                        if (!(val instanceof Map)) continue;

                        Map<String, Object> sessionData = (Map<String, Object>) val;
                        Timestamp ts = null;
                        Object tsObj = sessionData.get("timestamp");
                        if (tsObj instanceof Timestamp) {
                            ts = (Timestamp) tsObj;
                        }

                        String label;
                        if (ts != null) {
                            label = timeFormat.format(ts.toDate());
                        } else {
                            // fallback: partial uuid
                            label = uuidKey;
                        }

                        sessionIds.add(uuidKey);
                        sessionLabels.add(label);
                    }

                    if (sessionIds.size() <= 1) {
                        cardSession.setVisibility(View.GONE);
                        cardHistory.setVisibility(View.GONE);
                        return;
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            sessionLabels
                    );
                    adapter.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item);
                    spSession.setAdapter(adapter);
                    cardSession.setVisibility(View.VISIBLE);

                    spSession.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position == 0) {
                                selectedSessionUUID = null;
                                cardHistory.setVisibility(View.GONE);
                                return;
                            }

                            selectedSessionUUID = sessionIds.get(position);
                            tvHistoryHeader.setText("Attendance – " + date);
                            loadAttendanceForSession(
                                    selectedCourseId,
                                    selectedScheduleId,
                                    date,
                                    selectedSessionUUID
                            );
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });
                })
                .addOnFailureListener(e -> Log.e(TAG, "Sessions load failed: " + e.getMessage()));
    }

    // ---------------------- ATTENDANCE FOR ONE SESSION ----------------------
    private void loadAttendanceForSession(String courseId,
                                          String scheduleId,
                                          String date,
                                          String sessionUUID) {

        cardHistory.setVisibility(View.VISIBLE);
        progressHistory.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);
        historyRows.clear();
        historyAdapter.notifyDataSetChanged();

        // First get StudentsEnrolled from Schedule
        db.collection("Courses").document(courseId)
                .collection("Schedule").document(scheduleId)
                .get()
                .addOnSuccessListener(scheduleDoc -> {
                    if (!scheduleDoc.exists()) {
                        progressHistory.setVisibility(View.GONE);
                        tvNoData.setVisibility(View.VISIBLE);
                        return;
                    }

                    List<String> studentsEnrolled =
                            (List<String>) scheduleDoc.get("StudentsEnrolled");
                    if (studentsEnrolled == null || studentsEnrolled.isEmpty()) {
                        progressHistory.setVisibility(View.GONE);
                        tvNoData.setVisibility(View.VISIBLE);
                        return;
                    }

                    // Now get the Attendance doc for this date
                    db.collection("Courses").document(courseId)
                            .collection("Schedule").document(scheduleId)
                            .collection("Attendance").document(date)
                            .get()
                            .addOnSuccessListener(attDoc -> {
                                Map<String, Object> data = attDoc.getData();
                                Map<String, Boolean> presentMap = new HashMap<>();

                                // Initialize all as absent
                                for (String stuId : studentsEnrolled) {
                                    presentMap.put(stuId, false);
                                }

                                if (data != null && data.containsKey(sessionUUID)) {
                                    Object sessionObj = data.get(sessionUUID);
                                    if (sessionObj instanceof Map) {
                                        Map<String, Object> sessionData =
                                                (Map<String, Object>) sessionObj;

                                        Object saObj = sessionData.get("StudentAttendanceData");
                                        if (saObj instanceof Map) {
                                            Map<String, Object> allStudents =
                                                    (Map<String, Object>) saObj;

                                            for (Map.Entry<String, Object> stEntry :
                                                    allStudents.entrySet()) {

                                                String stuId = stEntry.getKey();
                                                if (!presentMap.containsKey(stuId)) continue;

                                                Object stuObj = stEntry.getValue();
                                                if (!(stuObj instanceof Map)) continue;

                                                Map<String, Object> stuData =
                                                        (Map<String, Object>) stuObj;

                                                String status =
                                                        (String) stuData.get("status");
                                                if (status != null &&
                                                        status.equalsIgnoreCase("Present")) {
                                                    presentMap.put(stuId, true);
                                                }
                                            }
                                        }
                                    }
                                }

                                // Build list of student attendance to fetch details
                                List<StudentAttendance> studentAttendanceList = new ArrayList<>();
                                for (String stuId : studentsEnrolled) {
                                    boolean present = Boolean.TRUE.equals(presentMap.get(stuId));
                                    studentAttendanceList.add(
                                            new StudentAttendance(stuId, present));
                                }

                                if (studentAttendanceList.isEmpty()) {
                                    progressHistory.setVisibility(View.GONE);
                                    tvNoData.setVisibility(View.VISIBLE);
                                    return;
                                }

                                final int[] remaining = {studentAttendanceList.size()};

                                for (StudentAttendance sa : studentAttendanceList) {
                                    db.collection("student").document(sa.studentId)
                                            .get()
                                            .addOnSuccessListener(stuDoc -> {
                                                String first = stuDoc.getString("FirstName");
                                                String last = stuDoc.getString("LastName");
                                                String email = stuDoc.getString("Email");

                                                String name = "";
                                                if (first != null) name += first + " ";
                                                if (last != null) name += last;

                                                if (name.trim().isEmpty()) name = sa.studentId;
                                                if (email == null) email = "";

                                                String display = name + "\n" + email;
                                                String statusLabel = sa.present ? "Present" : "Absent";

                                                historyRows.add(new ProfAttendanceRow(display, statusLabel));

                                                remaining[0]--;
                                                if (remaining[0] <= 0) finalizeHistory();
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Student doc load failed: " + e.getMessage());
                                                String display = sa.studentId;
                                                String statusLabel = sa.present ? "Present" : "Absent";

                                                historyRows.add(new ProfAttendanceRow(display, statusLabel));

                                                remaining[0]--;
                                                if (remaining[0] <= 0) finalizeHistory();
                                            });
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG,
                                        "Attendance doc load failed: " + e.getMessage());
                                progressHistory.setVisibility(View.GONE);
                                tvNoData.setVisibility(View.VISIBLE);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Schedule doc load failed: " + e.getMessage());
                    progressHistory.setVisibility(View.GONE);
                    tvNoData.setVisibility(View.VISIBLE);
                });
    }

    private void finalizeHistory() {
        progressHistory.setVisibility(View.GONE);

        if (historyRows.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
        } else {
            tvNoData.setVisibility(View.GONE);
        }

        historyAdapter.notifyDataSetChanged();
    }

    // ---------------------- MODEL + ADAPTER ----------------------
    private static class ProfAttendanceRow {
        final String student;  // now "Name\nEmail"
        final String status;

        ProfAttendanceRow(String student, String status) {
            this.student = student;
            this.status = status;
        }
    }

    private static class StudentAttendance {
        final String studentId;
        final boolean present;

        StudentAttendance(String studentId, boolean present) {
            this.studentId = studentId;
            this.present = present;
        }
    }

    private static class ProfHistoryAdapter
            extends RecyclerView.Adapter<ProfHistoryAdapter.HistoryViewHolder> {

        private final List<ProfAttendanceRow> items;

        ProfHistoryAdapter(List<ProfAttendanceRow> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(
                @NonNull android.view.ViewGroup parent,
                int viewType
        ) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_prof_attendance_row, parent, false);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(
                @NonNull HistoryViewHolder holder,
                int position
        ) {
            ProfAttendanceRow row = items.get(position);
            holder.tvStudent.setText(row.student);
            holder.tvStatus.setText(row.status);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class HistoryViewHolder extends RecyclerView.ViewHolder {
            TextView tvStudent, tvStatus;

            HistoryViewHolder(@NonNull android.view.View itemView) {
                super(itemView);
                tvStudent = itemView.findViewById(R.id.tvStudent);
                tvStatus = itemView.findViewById(R.id.tvStatus);
            }
        }
    }
}
