package com.example.class_space_z;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.class_space_z.adapters.StudentAdapter;
import com.example.class_space_z.models.Student;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class TeacherAttendanceActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private StudentAdapter studentAdapter;
    private final List<Student> studentList = new ArrayList<>();
    private final List<Student> filteredList = new ArrayList<>();

    private Button btnAll, btnPresent, btnAbsent;
    private EditText edtSearch;
    private ImageView btnBack;

    private FirebaseAuth auth;
    private DatabaseReference waitingRoomRef;
    private String classId, className;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_attendance);

        auth = FirebaseAuth.getInstance();

        classId = getIntent().getStringExtra("classId");
        className = getIntent().getStringExtra("className");
        if (className != null) setTitle(className);

        bindViews();
        setupRecycler();
        loadStudents();

        btnBack.setOnClickListener(v -> finish());
        setupFilters();
        setupSearch();
    }

    private void bindViews() {
        recyclerView = findViewById(R.id.recyclerAttendance);
        btnAll = findViewById(R.id.btnAll);
        btnPresent = findViewById(R.id.btnPresent);
        btnAbsent = findViewById(R.id.btnAbsent);
        edtSearch = findViewById(R.id.searchStudent);
        btnBack = findViewById(R.id.backButton);
        Button btnExportCSV = findViewById(R.id.btnExportCSV);
        btnExportCSV.setOnClickListener(v -> Toast.makeText(this, "CSV export (optional)", Toast.LENGTH_SHORT).show());
    }

    private void setupRecycler() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // ✅ Firebase Realtime Database reference
        waitingRoomRef = FirebaseDatabase.getInstance()
                .getReference("classes")
                .child(classId)
                .child("waitingRoom");

        studentAdapter = new StudentAdapter(filteredList, waitingRoomRef);
        recyclerView.setAdapter(studentAdapter);
    }

    private void loadStudents() {
        waitingRoomRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                studentList.clear();
                for (DataSnapshot d : snapshot.getChildren()) {
                    Student s = d.getValue(Student.class);
                    if (s != null) {
                        studentList.add(s);
                    }
                }
                filteredList.clear();
                filteredList.addAll(studentList);
                studentAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(TeacherAttendanceActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupFilters() {
        btnAll.setOnClickListener(v -> {
            filteredList.clear();
            filteredList.addAll(studentList);
            studentAdapter.notifyDataSetChanged();
        });
        btnPresent.setOnClickListener(v -> {
            filteredList.clear();
            for (Student s : studentList) {
                if ("admitted".equals(s.getStatus())) filteredList.add(s);
            }
            studentAdapter.notifyDataSetChanged();
        });
        btnAbsent.setOnClickListener(v -> {
            filteredList.clear();
            for (Student s : studentList) {
                if (!"admitted".equals(s.getStatus())) filteredList.add(s);
            }
            studentAdapter.notifyDataSetChanged();
        });
    }

    private void setupSearch() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filter(String q) {
        filteredList.clear();
        for (Student s : studentList) {
            if (s.getName() != null && s.getName().toLowerCase().contains(q.toLowerCase())) {
                filteredList.add(s);
            }
        }
        studentAdapter.notifyDataSetChanged();
    }
}