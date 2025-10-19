package com.example.class_space_z;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.backendless.BackendlessUser;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;
import com.backendless.persistence.DataQueryBuilder;

// ==== REQUIRED ADAPTER IMPORTS ====
import com.example.class_space_z.adapters.HeaderAdapter;
import com.example.class_space_z.adapters.SectionTitleAdapter;
import com.example.class_space_z.adapters.AssignmentAdapter;
import com.example.class_space_z.adapters.StudentClassAdapter;
// ==================================

import com.example.class_space_z.models.AssignmentItem;
import com.example.class_space_z.models.ClassModel;        // teacher-side table
import com.example.class_space_z.models.StudentClassItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudentDashboardActivity extends AppCompatActivity {

    private RecyclerView rvDashboard;

    private HeaderAdapter headerAdapter;
    private SectionTitleAdapter joinedTitleAdapter;
    private SectionTitleAdapter upcomingTitleAdapter;
    private StudentClassAdapter studentClassAdapter;
    private AssignmentAdapter assignmentAdapter;

    private MaterialToolbar topAppBar;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        topAppBar   = findViewById(R.id.topAppBar);
        bottomNav   = findViewById(R.id.bottom_nav);
        rvDashboard = findViewById(R.id.rvDashboard);
        rvDashboard.setLayoutManager(new LinearLayoutManager(this));

        // Toolbar setup
        if (topAppBar != null) {
            topAppBar.setNavigationOnClickListener(v ->
                    Toast.makeText(this, "Menu clicked", Toast.LENGTH_SHORT).show());
            topAppBar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_join) { showJoinDialog(); return true; }
                return false;
            });
        }

        // Bottom navigation setup
        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(item -> {
                if (item.getItemId() == R.id.nav_add) { showJoinDialog(); return true; }
                return true;
            });
        }

        headerAdapter        = new HeaderAdapter("Welcome back, Sagor");
        joinedTitleAdapter   = new SectionTitleAdapter("Joined Classes");
        upcomingTitleAdapter = new SectionTitleAdapter("Upcoming Assignments");
        studentClassAdapter  = new StudentClassAdapter(
                this,
                item -> {  // item click → details screen
                    Intent it = new Intent(this, StudentClassDetailsActivity.class);
                    it.putExtra(StudentClassDetailsActivity.EXTRA_CLASS_ID,   item.getClassRefId());
                    it.putExtra(StudentClassDetailsActivity.EXTRA_CLASS_NAME, item.getName());
                    it.putExtra(StudentClassDetailsActivity.EXTRA_LIVE_URL,   item.getLiveUrl());
                    startActivity(it);
                },
                null
        );
        assignmentAdapter    = new AssignmentAdapter(new ArrayList<AssignmentItem>());

        ConcatAdapter concat = new ConcatAdapter(
                headerAdapter,
                joinedTitleAdapter,
                studentClassAdapter,
                upcomingTitleAdapter,
                assignmentAdapter
        );
        rvDashboard.setAdapter(concat);

        loadStudentClasses();
        loadAssignments();
    }

    // ---------- LOAD: joined classes ----------
    private void loadStudentClasses() {
        String uid = currentUserId();
        if (TextUtils.isEmpty(uid)) {
            Toast.makeText(this, "Please sign in", Toast.LENGTH_SHORT).show();
            studentClassAdapter.setData(new ArrayList<StudentClassItem>());
            return;
        }

        String where = "studentIds LIKE '%" + uid + "%'";
        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause(where);
        qb.setPageSize(100);
        qb.setSortBy(java.util.Collections.singletonList("created DESC"));

        com.backendless.Backendless.Data.of(StudentClassItem.class).find(qb,
                new AsyncCallback<List<StudentClassItem>>() {
                    @Override
                    public void handleResponse(List<StudentClassItem> response) {
                        List<StudentClassItem> data = (response == null)
                                ? new ArrayList<StudentClassItem>() : response;
                        studentClassAdapter.setData(data);
                        enrichFromTeacherClass(data);
                    }
                    @Override
                    public void handleFault(BackendlessFault fault) {
                        // fallback demo data
                        List<StudentClassItem> demo = new ArrayList<>();
                        StudentClassItem s = new StudentClassItem();
                        s.setLabel("Class 1");
                        s.setName("Web Technologies");
                        s.setTeacher("Somapika Das");
                        s.setNextLive("5:00 PM");
                        s.setStudents(30);
                        s.setBatch("60");
                        s.setSection("C");
                        demo.add(s);
                        studentClassAdapter.setData(demo);
                    }
                });
    }

    private void enrichFromTeacherClass(List<StudentClassItem> studentItems) {
        if (studentItems == null || studentItems.isEmpty()) return;

        StringBuilder ids = new StringBuilder();
        Map<String, Integer> seen = new HashMap<>();
        for (StudentClassItem sc : studentItems) {
            String ref = sc.getClassRefId();
            if (TextUtils.isEmpty(ref) || seen.containsKey(ref)) continue;
            if (ids.length() > 0) ids.append("','");
            ids.append(ref);
            seen.put(ref, 1);
        }
        if (ids.length() == 0) return;

        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause("objectId in ('" + ids + "')");
        qb.setPageSize(100);

        com.backendless.Backendless.Data.of(ClassModel.class).find(qb, new AsyncCallback<List<ClassModel>>() {
            @Override public void handleResponse(List<ClassModel> list) {
                if (list == null) return;
                Map<String, ClassModel> map = new HashMap<>();
                for (ClassModel cm : list)
                    if (cm != null && cm.getId() != null) map.put(cm.getId(), cm);

                boolean changed = false;
                for (StudentClassItem sc : studentItems) {
                    ClassModel cm = map.get(sc.getClassRefId());
                    if (cm == null) continue;
                    sc.setName(nz(cm.getName(), sc.getName()));
                    sc.setTeacher(nz(cm.getTeacherName(), sc.getTeacher()));
                    sc.setNextLive(nz(cm.getNextLive(), sc.getNextLive()));
                    if (cm.getStudents() != null) sc.setStudents(cm.getStudents());
                    sc.setBatch(nz(cm.getBatch(), sc.getBatch()));
                    sc.setSection(nz(cm.getSection(), sc.getSection()));
                    sc.setGoToUrl(nz(cm.getGoToUrl(), sc.getGoToUrl()));
                    sc.setLiveUrl(nz(cm.getLiveUrl(), sc.getLiveUrl()));
                    changed = true;
                }
                if (changed) studentClassAdapter.notifyDataSetChanged();
            }
            @Override public void handleFault(BackendlessFault fault) { /* ignore */ }
        });
    }

    // ---------- LOAD: assignments ----------
    private void loadAssignments() {
        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setPageSize(50);
        com.backendless.Backendless.Data.of(AssignmentItem.class).find(qb,
                new AsyncCallback<List<AssignmentItem>>() {
                    @Override
                    public void handleResponse(List<AssignmentItem> response) {
                        assignmentAdapter.setData(response == null
                                ? new ArrayList<AssignmentItem>()
                                : response);
                    }

                    @Override
                    public void handleFault(BackendlessFault fault) {
                        // ✅ fixed fallback list (matches constructor with 3 args)
                        ArrayList<AssignmentItem> fallback = new ArrayList<>();
                        fallback.add(new AssignmentItem("Math Homework", "Due: Tomorrow", ""));
                        fallback.add(new AssignmentItem("History Essay", "Due: Next Week", ""));
                        assignmentAdapter.setData(fallback);
                    }
                });
    }

    // ---------- join by code (+) ----------
    private void showJoinDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_join_by_code, null);
        EditText et = view.findViewById(R.id.etInvite);

        final androidx.appcompat.app.AlertDialog dlg =
                new MaterialAlertDialogBuilder(this).setView(view).create();

        View cancel = view.findViewById(R.id.btnCancel);
        View join   = view.findViewById(R.id.btnJoin);

        if (cancel != null) cancel.setOnClickListener(v -> dlg.dismiss());
        if (join   != null) join.setOnClickListener(v -> {
            String code = et.getText() != null ? et.getText().toString().trim() : "";
            joinClassByInvite(code);
            dlg.dismiss();
        });

        dlg.show();
    }

    private void joinClassByInvite(String inviteCode) {
        if (TextUtils.isEmpty(inviteCode)) {
            Toast.makeText(this, "Enter a valid invite code", Toast.LENGTH_SHORT).show();
            return;
        }

        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause("inviteCode = '" + inviteCode + "'");
        qb.setPageSize(1);

        com.backendless.Backendless.Data.of(ClassModel.class).find(qb, new AsyncCallback<List<ClassModel>>() {
            @Override
            public void handleResponse(List<ClassModel> resp) {
                if (resp == null || resp.isEmpty()) {
                    Toast.makeText(StudentDashboardActivity.this, "Invalid code", Toast.LENGTH_SHORT).show();
                    return;
                }
                ClassModel src = resp.get(0);
                upsertStudentClassFrom(src);
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Toast.makeText(StudentDashboardActivity.this, "Join failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void upsertStudentClassFrom(ClassModel src) {
        String uid = currentUserId();
        if (TextUtils.isEmpty(uid)) {
            Toast.makeText(this, "Login required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Simple create (ডুপ্লিকেট এড়াতে চাইলে আগে classRefId দিয়ে find করে আপডেট করো)
        StudentClassItem sc = new StudentClassItem();
        sc.setClassRefId(src.getId());
        sc.setLabel(nz(src.getLabel(), "Class 1"));
        sc.setName(nz(src.getName(), ""));
        sc.setTeacher(nz(src.getTeacherName(), ""));
        sc.setNextLive(nz(src.getNextLive(), ""));
        sc.setStudents(src.getStudents());
        sc.setBatch(nz(src.getBatch(), ""));
        sc.setSection(nz(src.getSection(), ""));
        sc.setInviteCode(nz(src.getInviteCode(), ""));
        sc.setGoToUrl(nz(src.getGoToUrl(), ""));
        sc.setLiveUrl(nz(src.getLiveUrl(), ""));
        sc.setStudentIds(uid);

        com.backendless.Backendless.Data.of(StudentClassItem.class).save(sc, new AsyncCallback<StudentClassItem>() {
            @Override
            public void handleResponse(StudentClassItem saved) {
                Toast.makeText(StudentDashboardActivity.this, "Joined!", Toast.LENGTH_SHORT).show();
                loadStudentClasses();
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Toast.makeText(StudentDashboardActivity.this, "Save failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------- helpers ----------
    private String currentUserId() {
        BackendlessUser cu = com.backendless.Backendless.UserService.CurrentUser();
        if (cu != null) return cu.getObjectId();
        String logged = com.backendless.Backendless.UserService.loggedInUser();
        return TextUtils.isEmpty(logged) ? null : logged;
    }

    private static String nz(String s, String f) {
        return (s == null || s.trim().isEmpty()) ? f : s;
    }
}
