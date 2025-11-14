package com.codecatalyst.smartattendance.smartattendanceadmin;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FirestoreDebug";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;

    private FirebaseFirestore db;
    private Spinner spinnerCourses;
    private TextView tvWelcome, tvSemester, tvSchedule, tvDebugInfo;
    private Button btnStartAttendance, btnStopAttendance, btnManualfallback;
    private LottieAnimationView animationBluetooth;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;

    private String professorId;
    private String selectedCourseId;
    private String selectedScheduleId;
    private String activeSessionUUID = null;
    private String advertisedUUID = null;

    private final ArrayList<String> courseNames = new ArrayList<>();
    private final ArrayList<String> courseIds = new ArrayList<>();

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar & Drawer
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_admin) {
                startActivity(new Intent(this, AdminLoginActivity.class));
                drawerLayout.closeDrawers();
                return true;
            }
            return false;
        });

        // Firebase & BLE
        db = FirebaseFirestore.getInstance();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        // Views
        tvWelcome = findViewById(R.id.tvWelcome);
        spinnerCourses = findViewById(R.id.spinnerCourses);
        tvSemester = findViewById(R.id.tvSemester);
        tvSchedule = findViewById(R.id.tvSchedule);
        tvDebugInfo = findViewById(R.id.tvDebugInfo);
        btnStartAttendance = findViewById(R.id.btnStartAttendance);
        btnStopAttendance = findViewById(R.id.btnStopAttendance);
        btnManualfallback = findViewById(R.id.btnManualfallback);

        animationBluetooth = findViewById(R.id.animationBluetooth);

        // Initial states
        btnStartAttendance.setEnabled(false);
        btnStopAttendance.setEnabled(false);
        tvDebugInfo.setVisibility(View.GONE);

        // Professor ID from login
        professorId = getIntent().getStringExtra("PROFESSOR_ID");

        // Permissions & data
        checkBluetoothPermissions();
        fetchProfessorCourses();

        // Button listeners
        btnStartAttendance.setOnClickListener(v -> checkExistingActiveSessionAndProceed());
        btnStopAttendance.setOnClickListener(v -> stopAttendanceSession());
        btnManualfallback.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, FallbackActivity.class)));
//        btnPrevious.setOnClickListener(v -> navigateToLogin());

        // Handle device back press
        this.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateToLogin();
            }
        });
    }

    // 🔹 Navigate to LoginActivity
    private void navigateToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // 🔹 Check and request Bluetooth permissions
    private void checkBluetoothPermissions() {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    // 🔹 Fetch professor’s courses
    private void fetchProfessorCourses() {
        db.collection("Professor").document(professorId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String name = document.getString("Name");
                        tvWelcome.setText("Welcome, " + name);
                        ArrayList<String> courses = (ArrayList<String>) document.get("coursesTaught");
                        if (courses != null && !courses.isEmpty()) {
                            for (String courseId : courses) fetchCourseDetails(courseId);
                        }
                    }
                });
    }

    private void fetchCourseDetails(String courseId) {
        db.collection("Courses").document(courseId)
                .get()
                .addOnSuccessListener(courseDoc -> {
                    if (courseDoc.exists()) {
                        courseNames.add(courseDoc.getString("CourseName"));
                        courseIds.add(courseId);
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                                android.R.layout.simple_spinner_dropdown_item, courseNames);
                        spinnerCourses.setAdapter(adapter);
                        spinnerCourses.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                selectedCourseId = courseIds.get(position);
                                fetchScheduleForCourse(selectedCourseId);
                            }
                            @Override public void onNothingSelected(AdapterView<?> parent) {}
                        });
                    }
                });
    }

    private void fetchScheduleForCourse(String courseId) {
        db.collection("Courses").document(courseId)
                .collection("Schedule")
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        var doc = qs.getDocuments().get(0);
                        selectedScheduleId = doc.getId();

                        String day = doc.getString("Day");
                        String startTime = doc.getString("StartTime");
                        String endTime = doc.getString("EndTime");
                        String semesterId = doc.getString("Semester");

                        db.collection("Semester").document(semesterId)
                                .get()
                                .addOnSuccessListener(semesterDoc -> {
                                    if (semesterDoc.exists()) {
                                        tvSemester.setText("Semester: " + semesterDoc.getString("Name"));
                                    } else tvSemester.setText("Semester: Unknown");
                                });

                        tvSchedule.setText("Day: " + day + " | " + startTime + " - " + endTime);
                        btnStartAttendance.setEnabled(true);
                    }
                });
    }

    // 🔹 Active session check
    private void checkExistingActiveSessionAndProceed() {
        String today = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? LocalDate.now().toString() : "unknown";

        db.collection("Courses").document(selectedCourseId)
                .collection("Schedule").document(selectedScheduleId)
                .collection("Attendance").document(today)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.getData() != null) {
                        Map<String, Object> sessions = doc.getData();
                        for (Map.Entry<String, Object> entry : sessions.entrySet()) {
                            if (entry.getValue() instanceof Map) {
                                Map<String, Object> sessionData = (Map<String, Object>) entry.getValue();
                                if ("Active".equals(sessionData.get("Status"))) {
                                    String activeUUID = (String) sessionData.get("SessionUUID");
                                    showCloseActiveSessionDialog(activeUUID);
                                    return;
                                }
                            }
                        }
                    }
                    startAttendanceSession();
                });
    }

    private void showCloseActiveSessionDialog(String activeUUID) {
        new AlertDialog.Builder(this)
                .setTitle("Active Session Found")
                .setMessage("An attendance session is already active. Close it and start a new one?")
                .setPositiveButton("Close & Start New", (dialog, which) -> {
                    String today = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            ? LocalDate.now().toString() : "unknown";

                    db.collection("Courses").document(selectedCourseId)
                            .collection("Schedule").document(selectedScheduleId)
                            .collection("Attendance").document(today)
                            .update(activeUUID + ".Status", "Closed")
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Previous session closed.", Toast.LENGTH_SHORT).show();
                                startAttendanceSession();
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to close previous session: " + e.getMessage()));
                })
                .setNegativeButton("Keep Current", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // 🔹 BLE Attendance
    private void startAttendanceSession() {
        advertisedUUID = UUID.randomUUID().toString();
        activeSessionUUID = advertisedUUID;

        if (advertiser == null) {
            Toast.makeText(this, "BLE Advertiser not supported", Toast.LENGTH_LONG).show();
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new android.os.ParcelUuid(UUID.fromString(advertisedUUID)))
                .setIncludeDeviceName(false)
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                btnStartAttendance.setEnabled(false);
                btnStopAttendance.setEnabled(true);

                animationBluetooth.setAlpha(0f);
                animationBluetooth.setVisibility(android.view.View.VISIBLE);
                animationBluetooth.animate().alpha(1f).setDuration(500).start();
                animationBluetooth.playAnimation();

                String debugText = "📡 Advertising UUID:\n" + advertisedUUID;
                tvDebugInfo.setText(debugText);
                tvDebugInfo.setVisibility(android.view.View.VISIBLE);

                Log.d(TAG, debugText);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    writeAttendanceToFirestore(advertisedUUID, LocalDate.now().toString());
                }
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e(TAG, "BLE Advertising failed: " + errorCode);
                Toast.makeText(MainActivity.this, "BLE Advertising failed: " + errorCode, Toast.LENGTH_SHORT).show();
                animationBluetooth.cancelAnimation();
                animationBluetooth.setVisibility(android.view.View.GONE);
                btnStartAttendance.setEnabled(true);
            }
        };

        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    private void writeAttendanceToFirestore(String uuid, String date) {
        DocumentReference ref = db.collection("Courses").document(selectedCourseId)
                .collection("Schedule").document(selectedScheduleId)
                .collection("Attendance").document(date);
        Map<String, Object> s = new HashMap<>();
        s.put("SessionUUID", uuid);
        s.put("Status", "Active");
        s.put("timestamp", Timestamp.now());
        Map<String, Object> d = new HashMap<>();
        d.put(uuid, s);
        ref.set(d, com.google.firebase.firestore.SetOptions.merge());
    }

    private void stopAttendanceSession() {
        if (advertiser != null && advertiseCallback != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                    != PackageManager.PERMISSION_GRANTED) return;

            advertiser.stopAdvertising(advertiseCallback);

            animationBluetooth.animate().alpha(0f).setDuration(500)
                    .withEndAction(() -> {
                        animationBluetooth.cancelAnimation();
                        animationBluetooth.setVisibility(android.view.View.GONE);
                    }).start();

            btnStartAttendance.setEnabled(true);
            btnStopAttendance.setEnabled(false);

            tvDebugInfo.setText("📴 Broadcast stopped.\nLast UUID: " + advertisedUUID);
            tvDebugInfo.setVisibility(android.view.View.VISIBLE);

//            if (activeSessionUUID != null) updateAttendanceStatus(activeSessionUUID, LocalDate.now().toString());
        }
    }

    private void updateAttendanceStatus(String uuid, String date) {
        db.collection("Courses").document(selectedCourseId)
                .collection("Schedule").document(selectedScheduleId)
                .collection("Attendance").document(date)
                .update(uuid + ".Status", "Closed")
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Session closed for UUID: " + uuid))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to update status: " + e.getMessage()));
    }
}
