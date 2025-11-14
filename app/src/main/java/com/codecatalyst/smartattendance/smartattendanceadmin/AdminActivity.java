package com.codecatalyst.smartattendance.smartattendanceadmin;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class AdminActivity extends AppCompatActivity { // extend AppCompatActivity
    private Button btnCourses, btnProfessor, btnSemester, btnStudent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Admin");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        btnCourses = findViewById(R.id.btnCourses);
        btnProfessor = findViewById(R.id.btnProfessor);
        btnSemester = findViewById(R.id.btnSemester);
        btnStudent = findViewById(R.id.btnStudent);


        btnSemester.setOnClickListener(v ->
                startActivity(new Intent(this, AddSemesterActivity.class)));
        btnStudent.setOnClickListener(v ->
                startActivity(new Intent(this, AddStudentActivity.class)));
        btnProfessor.setOnClickListener(v ->
                startActivity(new Intent(this, AddProfessorActivity.class)));
        btnCourses.setOnClickListener(v ->
                startActivity(new Intent(this, AddCourseActivity.class)));
    }

    private void openPlaceholder(String section) {
        Intent i = new Intent(this, AdminSectionActivity.class);
        i.putExtra("SECTION", section);
        startActivity(i);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}