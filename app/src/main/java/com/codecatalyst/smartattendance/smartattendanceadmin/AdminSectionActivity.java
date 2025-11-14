package com.codecatalyst.smartattendance.smartattendanceadmin;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class AdminSectionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_section);

        String section = getIntent().getStringExtra("SECTION");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(section != null ? section : "Admin");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView tv = findViewById(R.id.tvSectionTitle);
        tv.setText((section != null ? section : "Admin") + " — TODO: wire Firestore CRUD");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

}