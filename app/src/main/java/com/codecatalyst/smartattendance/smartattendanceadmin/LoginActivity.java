package com.codecatalyst.smartattendance.smartattendanceadmin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class LoginActivity extends AppCompatActivity {

    private EditText emailField, passwordField;
    private Button loginButton;

    private FirebaseFirestore db;
    private static final String TAG = "FirestoreDebug";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        loginButton = findViewById(R.id.loginButton);

        db = FirebaseFirestore.getInstance();

        loginButton.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            loginProfessor(email, password);
        });
    }

    private void loginProfessor(String email, String password) {
        Log.d(TAG, "Attempting login for: " + email);

        db.collection("Professor")
                .whereEqualTo("email", email)
                .limit(1) // safer & faster
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(this, "No professor found with this email", Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "No professor found with email: " + email);
                        return;
                    }

                    DocumentSnapshot doc = querySnapshot.getDocuments().get(0);

                    String storedPassword = doc.getString("password");
                    if (storedPassword == null) {
                        Toast.makeText(this, "Password missing in database", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!storedPassword.equals(password)) {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "❌ Wrong password for " + email);
                        return;
                    }

                    String professorName = doc.getString("Name");
                    String professorId = doc.getId();

                    Toast.makeText(this, "Welcome " + professorName, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Login Success → " + professorName);

                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.putExtra("PROFESSOR_ID", professorId);
                    intent.putExtra("EMAIL", email);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore Error: " + e.getMessage(), e);
                    Toast.makeText(this, "Login failed. Try again later.", Toast.LENGTH_SHORT).show();
                });
    }
}
