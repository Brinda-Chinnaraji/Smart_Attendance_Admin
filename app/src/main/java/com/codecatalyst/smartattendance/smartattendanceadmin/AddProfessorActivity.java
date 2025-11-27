    package com.codecatalyst.smartattendance.smartattendanceadmin;
    import android.os.Bundle;
    import android.text.SpannableString;
    import android.text.TextUtils;
    import android.text.style.AbsoluteSizeSpan;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.widget.Button;
    import android.widget.EditText;
    import android.widget.LinearLayout;
    import android.widget.TextView;
    import android.widget.Toast;

    import androidx.appcompat.app.AlertDialog;
    import androidx.appcompat.app.AppCompatActivity;

    import com.google.firebase.firestore.DocumentReference;
    import com.google.firebase.firestore.DocumentSnapshot;
    import com.google.firebase.firestore.FieldValue;
    import com.google.firebase.firestore.FirebaseFirestore;
    import com.google.firebase.firestore.Query;
    import com.google.firebase.firestore.WriteBatch;
    import java.util.ArrayList;
    import java.util.HashMap;
    import java.util.List;
    import java.util.Map;

    public class AddProfessorActivity extends AppCompatActivity {
        private EditText etName, etEmail, etPassword, etDepartment, etProfessorCode;
        private TextView tvPickedCourses;
        private Button btnPickExistingCourses, btnQuickNewCourse, btnSaveProfessor;

        private final FirebaseFirestore db = FirebaseFirestore.getInstance();
        private final List<String> selectedExistingCourseIds = new ArrayList<>();
        private final List<Map<String, Object>> pendingNewCourses = new ArrayList<>();

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_add_professor);
            EditText et = findViewById(R.id.etProfessorCode);
            SpannableString hint = new SpannableString("Professor Code (e.g., PROF-JS001)");
            hint.setSpan(new AbsoluteSizeSpan(12, true), 0, hint.length(), 0); // 12sp
            et.setHint(hint);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("Add Professor");
            }


            etName = findViewById(R.id.etName);
            etEmail = findViewById(R.id.etEmail);
            etPassword = findViewById(R.id.etPassword);
            etDepartment = findViewById(R.id.etDepartment);
            etProfessorCode = findViewById(R.id.etProfessorCode);

            tvPickedCourses = findViewById(R.id.tvPickedCourses);
            btnPickExistingCourses = findViewById(R.id.btnPickExistingCourses);
            btnQuickNewCourse = findViewById(R.id.btnQuickNewCourse);
            btnSaveProfessor = findViewById(R.id.btnSaveProfessor);

            btnPickExistingCourses.setOnClickListener(v -> pickExistingCourses());
            btnQuickNewCourse.setOnClickListener(v -> showQuickNewCourseDialog());
            btnSaveProfessor.setOnClickListener(v -> saveProfessor());
        }

        private void pickExistingCourses() {
            db.collection("Courses").get().addOnSuccessListener(qs -> {
                List<String> labels = new ArrayList<>();
                List<String> ids = new ArrayList<>();
                for (DocumentSnapshot d : qs) {
                    labels.add(d.getString("CourseName"));
                    ids.add(d.getId());
                }
                boolean[] checked = new boolean[ids.size()];
                String[] items = labels.toArray(new String[0]);


                new AlertDialog.Builder(this)
                        .setTitle("Select Courses")
                        .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                            String id = ids.get(which);
                            if (isChecked && !selectedExistingCourseIds.contains(id)) {
                                selectedExistingCourseIds.add(id);
                            } else if (!isChecked) {
                                selectedExistingCourseIds.remove(id);
                            }
                        })
                        .setPositiveButton("Done", (d, w) ->
                                tvPickedCourses.setText("Selected: " + selectedExistingCourseIds.size() +
                                        " existing, " + pendingNewCourses.size() + " new"))
                        .show();
            }).addOnFailureListener(e ->
                    Toast.makeText(this, "Load courses failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }

        private void showQuickNewCourseDialog() {
            // Simple inline form for a minimal Course
            View dummy = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null);
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(40, 20, 40, 0);

            EditText etCourseName = new EditText(this);
            etCourseName.setHint("Course Name");
            EditText etShortCode = new EditText(this);
            etShortCode.setHint("Short Code (ITCS1234)");

            container.addView(etCourseName);
            container.addView(etShortCode);

            new AlertDialog.Builder(this)
                    .setTitle("Quick New Course")
                    .setView(container)
                    .setPositiveButton("Add", (dialog, which) -> {
                        String name = etCourseName.getText().toString().trim();
                        String sc = etShortCode.getText().toString().trim();
                        if (name.isEmpty() || sc.isEmpty()) {
                            Toast.makeText(this, "Course name and short code required.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Map<String, Object> course = new HashMap<>();
                        course.put("CourseName", name);
                        course.put("ShortCode", sc);
                        pendingNewCourses.add(course);
                        tvPickedCourses.setText("Selected: " + selectedExistingCourseIds.size() +
                                " existing, " + pendingNewCourses.size() + " new");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void saveProfessor() {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String department = etDepartment.getText().toString().trim();
            String professorCode = etProfessorCode.getText().toString().trim();

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Name, email, and password are required.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Optional uniqueness check for email
            Query q = db.collection("Professor").whereEqualTo("email", email);
            q.get().addOnSuccessListener(qs -> {
                if (!qs.isEmpty()) {
                    Toast.makeText(this, "Email already exists.", Toast.LENGTH_SHORT).show();
                    return;
                }
                writeProfessorAndLinks(name, email, password, department, professorCode);
            }).addOnFailureListener(e ->
                    Toast.makeText(this, "Check failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }

        private void writeProfessorAndLinks(String name, String email, String password,
                                            String department, String professorCode) {

            WriteBatch batch = db.batch(); // FIX: ensure WriteBatch import present
            DocumentReference profRef = db.collection("Professor").document();

            Map<String, Object> prof = new HashMap<>();
            prof.put("Name", name);
            prof.put("email", email);
            prof.put("password", password);
            prof.put("department", department);
            prof.put("professorCode", professorCode);
            prof.put("coursesTaught", new ArrayList<String>());

            batch.set(profRef, prof); // FIX: correct method on WriteBatch

            // Link existing courses
            for (String courseId : selectedExistingCourseIds) {
                DocumentReference cRef = db.collection("Courses").document(courseId);
                batch.update(cRef, "ProfessorID", profRef.getId());
                batch.update(profRef, "coursesTaught", FieldValue.arrayUnion(courseId));
            }

            // Create quick new courses and link
            for (Map<String, Object> nc : pendingNewCourses) {
                DocumentReference cRef = db.collection("Courses").document();
                nc.put("ProfessorID", profRef.getId());
                batch.set(cRef, nc);
                batch.update(profRef, "coursesTaught", FieldValue.arrayUnion(cRef.getId()));
            }

            batch.commit() // FIX: commit() exists on WriteBatch
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Professor saved and courses linked.", Toast.LENGTH_LONG).show();
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