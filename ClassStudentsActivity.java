package com.example.class_space_z;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.backendless.Backendless;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;
import com.backendless.persistence.DataQueryBuilder;
import com.example.class_space_z.adapters.StudentListAdapter;
import com.example.class_space_z.models.Student;   // ✅ এখানে Student import করো

import java.util.ArrayList;
import java.util.List;

public class ClassStudentsActivity extends AppCompatActivity {

    private RecyclerView recyclerStudents;
    private StudentListAdapter adapter;
    private final List<Student> studentList = new ArrayList<>(); // ✅ Student

    private TextView tvClassName;
    private ImageButton btnBack;

    private String classId;
    private String className;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_students);

        recyclerStudents = findViewById(R.id.recyclerStudents);
        tvClassName = findViewById(R.id.tvClassName);
        btnBack = findViewById(R.id.btn_back);

        if (getIntent() != null) {
            classId = getIntent().getStringExtra("classId");
            className = getIntent().getStringExtra("className");
        }

        if (classId == null || classId.trim().isEmpty()) {
            Toast.makeText(this, "Missing class ID!", Toast.LENGTH_SHORT).show();
            finish(); return;
        }
        if (className == null || className.trim().isEmpty()) className = "Class Students";
        tvClassName.setText(className);

        recyclerStudents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StudentListAdapter(this, studentList);  // ✅ List<Student>
        recyclerStudents.setAdapter(adapter);

        loadStudents();

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadStudents() {
        String whereClause = "classId = '" + classId.replace("'", "''") + "'";
        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause(whereClause);
        qb.setPageSize(100);

        Backendless.Data.of(Student.class).find(qb, new AsyncCallback<List<Student>>() { // ✅ Student.class
            @Override
            public void handleResponse(List<Student> response) {
                studentList.clear();
                if (response != null) {
                    for (Student s : response) {
                        // যদি adapter পুরনো id field ব্যবহার করে, নিশ্চিত করো id ফিল্ডে objectId পড়ে:
                        if (s.getId() == null && s.getObjectId() != null) {
                            s.setId(s.getObjectId());
                        }
                        studentList.add(s);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Toast.makeText(ClassStudentsActivity.this,
                        "Error loading students: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
