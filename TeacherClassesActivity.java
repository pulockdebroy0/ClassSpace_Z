package com.example.class_space_z;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;
import com.backendless.persistence.DataQueryBuilder;
import com.backendless.rt.data.EventHandler;
import com.example.class_space_z.adapters.ClassAdapter;
import com.example.class_space_z.models.ClassModel;
import com.example.class_space_z.models.StudentClassItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TeacherClassesActivity extends AppCompatActivity {

    private View btnMenu, btnAddClass;
    private TextView tvTitle;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private ClassAdapter adapter;
    private String teacherObjectId;
    private EventHandler<ClassModel> rtHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_classes);

        tvTitle = findViewById(R.id.tvTitle);
        btnMenu = findViewById(R.id.btnMenu);
        btnAddClass = findViewById(R.id.btnAddClass);

        if (tvTitle != null) tvTitle.setText(getString(R.string.title_classes));
        setupHeader();

        recyclerView = findViewById(R.id.recyclerViewClasses);
        emptyView = findViewById(R.id.tvNoClasses);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            adapter = new ClassAdapter(
                    this,
                    item -> {
                        String id = safeId(item);
                        if (id == null) {
                            Toast.makeText(this, "Invalid class", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Intent i = new Intent(this, TeacherClassDetailsActivity.class);
                        i.putExtra(TeacherClassDetailsActivity.EXTRA_CLASS_ID, id);
                        i.putExtra(TeacherClassDetailsActivity.EXTRA_CLASS_NAME, item.getName());
                        startActivity(i);
                    },
                    this::confirmDelete
            );
            recyclerView.setAdapter(adapter);
        }

        setupBottomNav();
    }

    private void setupHeader() {
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v ->
                    Toast.makeText(this, "Menu clicked", Toast.LENGTH_SHORT).show());
        }
        if (btnAddClass != null) {
            btnAddClass.setOnClickListener(v -> showJoinDialog());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Backendless.UserService.isValidLogin(new AsyncCallback<Boolean>() {
            @Override
            public void handleResponse(Boolean isValid) {
                if (!Boolean.TRUE.equals(isValid)) {
                    if (adapter != null) adapter.submitList(new ArrayList<>());
                    showEmptyIfNeeded(true);
                    Toast.makeText(TeacherClassesActivity.this, "Please sign in", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(TeacherClassesActivity.this, SignInActivity.class));
                    finish();
                    return;
                }
                BackendlessUser cu = Backendless.UserService.CurrentUser();
                teacherObjectId = (cu != null) ? cu.getObjectId() : Backendless.UserService.loggedInUser();
                loadClasses();
                if (!TextUtils.isEmpty(teacherObjectId)) attachRealtime();
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Toast.makeText(TeacherClassesActivity.this, "Login check failed", Toast.LENGTH_SHORT).show();
                showEmptyIfNeeded(true);
            }
        });
    }

    @Override
    protected void onStop() {
        detachRealtime();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        detachRealtime();
        super.onDestroy();
    }

    private void loadClasses() {
        if (TextUtils.isEmpty(teacherObjectId)) {
            if (adapter != null) adapter.submitList(new ArrayList<>());
            showEmptyIfNeeded(true);
            return;
        }
        String where = "teacherId = '" + esc(teacherObjectId) + "'";
        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause(where);
        qb.setSortBy(Collections.singletonList("created ASC"));
        qb.setPageSize(100);

        Backendless.Data.of(ClassModel.class).find(qb, new AsyncCallback<List<ClassModel>>() {
            @Override
            public void handleResponse(List<ClassModel> list) {
                List<ClassModel> out = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
                sortByCreated(out);
                if (adapter != null) adapter.submitList(out);
                showEmptyIfNeeded(out.isEmpty());
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Toast.makeText(TeacherClassesActivity.this, "Load failed: " + fault.getMessage(), Toast.LENGTH_LONG).show();
                showEmptyIfNeeded(true);
            }
        });
    }

    private void sortByCreated(List<ClassModel> list) {
        try {
            list.sort(Comparator.comparing(ClassModel::getCreated, Comparator.nullsLast(Comparator.naturalOrder())));
        } catch (Exception ignore) {
        }
    }

    private void showEmptyIfNeeded(boolean empty) {
        if (emptyView == null || recyclerView == null) return;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void attachRealtime() {
    }

    private void detachRealtime() {
        try {
            if (rtHandler != null) {
                rtHandler = null;
            }
        } catch (Exception ignore) {
        }
    }

    private void confirmDelete(final ClassModel item) {
    }

    private void performDelete(String objectId) {
    }

    private void setupBottomNav() {
        View bottom = findViewById(R.id.bottom_nav);
        if (bottom == null) return;

        View vDash = bottom.findViewById(R.id.navDashboard);
        View vClass = bottom.findViewById(R.id.navClasses);
        View vAttn = bottom.findViewById(R.id.navAttendance);
        View vProf = bottom.findViewById(R.id.navProfile);

        if (vDash != null) vDash.setOnClickListener(v ->
                startActivity(new Intent(this, TeacherDashboardActivity.class)));

        if (vClass != null) vClass.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.nav_classes), Toast.LENGTH_SHORT).show());

        if (vAttn != null) vAttn.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.nav_attendance), Toast.LENGTH_SHORT).show());

        if (vProf != null) vProf.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void showJoinDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_join_by_code, null);
        EditText et = view.findViewById(R.id.etInvite);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Join class by invite")
                .setView(view)
                .setPositiveButton("Join", (d, w) -> {
                    String code = et.getText() != null ? et.getText().toString().trim() : "";
                    joinClassByInvite(code);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void joinClassByInvite(String inviteCode) {
        if (TextUtils.isEmpty(inviteCode)) {
            Toast.makeText(this, "Enter a valid invite code", Toast.LENGTH_SHORT).show();
            return;
        }
        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause("inviteCode = '" + esc(inviteCode) + "'");
        qb.setPageSize(1);

        Backendless.Data.of(ClassModel.class).find(qb, new AsyncCallback<List<ClassModel>>() {
            @Override
            public void handleResponse(List<ClassModel> resp) {
                if (resp == null || resp.isEmpty()) {
                    Toast.makeText(TeacherClassesActivity.this, "Invalid code", Toast.LENGTH_SHORT).show();
                    return;
                }
                ClassModel src = resp.get(0);
                createStudentClass(src);
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Toast.makeText(TeacherClassesActivity.this, "Join failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createStudentClass(ClassModel src) {
        String uid = Backendless.UserService.CurrentUser() != null
                ? Backendless.UserService.CurrentUser().getObjectId()
                : Backendless.UserService.loggedInUser();

        if (TextUtils.isEmpty(uid)) {
            Toast.makeText(this, "Login required", Toast.LENGTH_SHORT).show();
            return;
        }

        StudentClassItem item = new StudentClassItem();
        item.setClassRefId(src.getId());
        item.setLabel(src.getLabel());
        item.setName(src.getName());
        item.setTeacher(src.getTeacherName());
        item.setNextLive(src.getNextLive());
        item.setStudents(src.getStudents());
        item.setBatch(src.getBatch());
        item.setSection(src.getSection());
        item.setInviteCode(src.getInviteCode());
        item.setStudentIds(uid);

        Backendless.Data.of(StudentClassItem.class).save(item, new AsyncCallback<StudentClassItem>() {
            @Override
            public void handleResponse(StudentClassItem saved) {
                Toast.makeText(TeacherClassesActivity.this, "Joined successfully!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(TeacherClassesActivity.this, StudentDashboardActivity.class));
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Toast.makeText(TeacherClassesActivity.this, "Save failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String safeId(ClassModel m) {
        if (m == null) return null;
        try {
            return m.getId();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
