package com.codecatalyst.smartattendance.smartattendanceadmin;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class AdminLoginActivity extends AppCompatActivity {

    EditText etEmail, etPassword;
    Button btnLogin, btnForgotPassword;
    ProgressBar progressBar;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etAdminEmail);
        etPassword = findViewById(R.id.etAdminPassword);
        btnLogin = findViewById(R.id.btnAdminloginButton);
        progressBar = findViewById(R.id.adminProgressBar);
        btnLogin.setOnClickListener(v -> loginAdmin());

    }

    private void loginAdmin() {
        String emailInput = etEmail.getText().toString().trim().toLowerCase(); // <-- lowercase
        String password = etPassword.getText().toString().trim();

        if (emailInput.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

      //  progressBar.setVisibility(View.VISIBLE);

        // 🔍 Query Admin collection where TA documents have matching email

                    Query query = db.collection("Admin").whereEqualTo("email", emailInput);
                    query.get()
                            .addOnSuccessListener(querySnapshot -> {
                                if (querySnapshot.isEmpty()) {
                                    Toast.makeText(this, "No professor found with this email"+ emailInput, Toast.LENGTH_SHORT).show();
                                    Log.w(TAG, "No professor found with email: " + emailInput);
                                    return;
                                }

                    // Expecting only one matching TA document
                    DocumentSnapshot taDoc = querySnapshot.getDocuments().get(0);
                    String storedPassword = taDoc.getString("Password");
                    String adminName = taDoc.getString("Name"); // fetch the Name field
                    String adminId = taDoc.getId();

                    if (storedPassword != null && storedPassword.equals(password)) {
                        Toast.makeText(this, "Welcome " + adminName, Toast.LENGTH_SHORT).show();

                        // Redirect to AdminActivity
                        Intent intent = new Intent(AdminLoginActivity.this, AdminActivity.class);
                        intent.putExtra("ADMIN_ID", adminId);
                        intent.putExtra("EMAIL", emailInput);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                   // progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

    }

}
