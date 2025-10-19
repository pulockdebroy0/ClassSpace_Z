package com.example.class_space_z;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.backendless.Backendless;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;
import com.backendless.persistence.DataQueryBuilder;
import com.example.class_space_z.adapters.TeacherAssignmentsAdapter;
import com.example.class_space_z.models.TeacherAssignmentRow;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class TeacherAssignmentsActivity extends AppCompatActivity
        implements TeacherAssignmentsAdapter.Listener {

    private RecyclerView rv;
    private TeacherAssignmentsAdapter adapter;

    private String classId;
    private String className;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_assignments);

        Backendless.initApp(getApplicationContext(),
                "8ACC0B61-36C3-499C-98EF-F7E8E78FFD19",
                "3C5A55CA-E265-4AB3-B857-F0C0DD98F380");

        Intent in = getIntent();
        classId   = safe(in.getStringExtra(TeacherClassDetailsActivity.EXTRA_CLASS_ID));
        className = safe(in.getStringExtra(TeacherClassDetailsActivity.EXTRA_CLASS_NAME));
        setTitle((className.isEmpty()? "Class" : className) + " • Assignments");

        rv = findViewById(R.id.rvTeacherAssignments);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TeacherAssignmentsAdapter(this);
        rv.setAdapter(adapter);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        View btnAdd = findViewById(R.id.btnAdd);
        if (btnAdd != null) btnAdd.setOnClickListener(v ->
                Toast.makeText(this, "Add clicked", Toast.LENGTH_SHORT).show()
        );

        loadAssignments();
    }

    private void loadAssignments() {
        showLoading(true);

        DataQueryBuilder qb = DataQueryBuilder.create();
        if (!classId.isEmpty()) {
            qb.setWhereClause("classId='" + classId + "'");
        }
        qb.setSortBy("created DESC");
        qb.setPageSize(100);

        Backendless.Data.of("Assignments").find(qb, new AsyncCallback<List<Map>>() {
            @Override public void handleResponse(List<Map> res) {
                List<TeacherAssignmentRow> rows = new ArrayList<>();
                if (res != null) {
                    for (Map a : res) {
                        String id       = safe((String) a.get("objectId"));
                        String title    = safe((String) a.get("title"));
                        String grade    = safe((String) a.get("grade"));
                        String feedback = safe((String) a.get("feedback"));
                        Object gradedAt = a.get("gradedAt");

                        String sub = buildSubtitle(grade, feedback, gradedAt);
                        rows.add(new TeacherAssignmentRow(title, sub, id));
                    }
                }
                adapter.setItems(rows);
                showLoading(false);
            }
            @Override public void handleFault(BackendlessFault fault) {
                Toast.makeText(TeacherAssignmentsActivity.this,
                        "Load failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private String buildSubtitle(String grade, String feedback, Object gradedAt) {
        String g = grade == null || grade.isEmpty() ? "Not graded" : ("Grade: " + grade);
        String f = feedback == null || feedback.isEmpty() ? "" : (" • " + feedback);
        String d = (gradedAt instanceof Date) ? (" • " + ((Date) gradedAt).toString()) : "";
        return g + f + d;
    }

    private void showLoading(boolean show) {
        if (rv != null) rv.setAlpha(show ? 0.5f : 1f);
        if (rv != null) rv.setEnabled(!show);
    }

    // -------- Listener methods --------
    @Override
    public void onViewClick(int pos, TeacherAssignmentRow row) {
        if (row == null) return;
        Intent i = new Intent(this, AssignmentDetailsActivity.class);
        i.putExtra(TeacherClassDetailsActivity.EXTRA_CLASS_ID, classId);
        i.putExtra(TeacherClassDetailsActivity.EXTRA_CLASS_NAME, className);
        i.putExtra(AssignmentDetailsActivity.EXTRA_ASSIGNMENT_ID, row.id);
        startActivity(i);
    }

    @Override public void onItemClick(int pos, TeacherAssignmentRow row) { onViewClick(pos, row); }

    @Override
    public void onItemLongClick(int pos, TeacherAssignmentRow row) {
        if (row == null) return;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete assignment?")
                .setMessage(row.title)
                .setPositiveButton("Delete", (DialogInterface dialog, int which) -> deleteAssignment(row))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------- DELETE using whereClause (SDK-compatible) --------
    private void deleteAssignment(TeacherAssignmentRow row) {
        showLoading(true);
        String where = "objectId = '" + safe(row.id) + "'";
        Backendless.Data.of("Assignments").remove(where, new AsyncCallback<Integer>() {
            @Override public void handleResponse(Integer count) {
                Toast.makeText(TeacherAssignmentsActivity.this,
                        (count != null && count > 0) ? "Deleted" : "Nothing deleted",
                        Toast.LENGTH_SHORT).show();
                loadAssignments();
            }
            @Override public void handleFault(BackendlessFault fault) {
                Toast.makeText(TeacherAssignmentsActivity.this,
                        "Delete failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
