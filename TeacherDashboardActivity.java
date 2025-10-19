package com.example.class_space_z;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.class_space_z.adapters.ClassAdapter;
import com.example.class_space_z.models.ClassModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;

import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;
import com.backendless.persistence.DataQueryBuilder;
import com.backendless.rt.data.EventHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TeacherDashboardActivity extends AppCompatActivity {

    private static final String TAG = "TeacherDashboard";

    private RecyclerView recyclerViewClasses;
    private ClassAdapter classAdapter;

    private TextView tvWelcome, tvAttendanceRate, tvAssignmentCount, tvSubmissionCount;

    private SharedPreferences prefs;

    // Backendless RT handler
    private EventHandler<ClassModel> rtHandler;
    private String teacherObjectId; // current user id

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_dashboard);

        prefs = getSharedPreferences("TeacherPrefs", Context.MODE_PRIVATE);

        tvWelcome           = findViewById(R.id.tvWelcome);
        recyclerViewClasses = findViewById(R.id.recyclerViewClasses);
        tvAttendanceRate    = findViewById(R.id.tvAttendanceRate);
        tvAssignmentCount   = findViewById(R.id.tvAssignmentCount);
        tvSubmissionCount   = findViewById(R.id.tvSubmissionCount);

        recyclerViewClasses.setLayoutManager(new LinearLayoutManager(this));

        // Tap -> details, Long-press -> delete
        classAdapter = new ClassAdapter(
                this,
                item -> {
                    try {
                        if (item == null) {
                            Toast.makeText(this, "Invalid class", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String cid = item.getId();
                        if (cid == null || cid.trim().isEmpty()) {
                            Toast.makeText(this, "Missing classId", Toast.LENGTH_SHORT).show();
                            Log.w(TAG, "onClick: item id is null. Ensure model.getId() returns objectId.");
                            return;
                        }
                        Intent i = new Intent(TeacherDashboardActivity.this, TeacherClassDetailsActivity.class);
                        i.putExtra(TeacherClassDetailsActivity.EXTRA_CLASS_ID,   cid);
                        i.putExtra(TeacherClassDetailsActivity.EXTRA_CLASS_NAME, item.getName());
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(TeacherDashboardActivity.this, "Open failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Open details failed", e);
                    }
                },
                this::confirmDelete
        );
        recyclerViewClasses.setAdapter(classAdapter);

        String teacherName = prefs.getString("teacherName", "Teacher");
        tvWelcome.setText("Welcome back, " + teacherName);

        wireAddButton();
        setupBottomNav();

        loadAttendanceStats();
        loadAssignmentStats();
        loadSubmissionStats();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // ✅ Reliable session check
        Backendless.UserService.isValidLogin(new AsyncCallback<Boolean>() {
            @Override public void handleResponse(Boolean isValid) {
                if (!Boolean.TRUE.equals(isValid)) {
                    goToSignIn();
                    return;
                }

                // 1) Try cached user
                BackendlessUser cu = Backendless.UserService.CurrentUser();
                if (cu != null) {
                    onUserReady(cu);
                    return;
                }

                // 2) Fallback: get last logged-in userId
                String uid = Backendless.UserService.loggedInUser();
                if (uid == null || uid.isEmpty()) {
                    uid = prefs.getString("loggedInUserId", null);
                }
                if (uid == null || uid.isEmpty()) {
                    goToSignIn();
                    return;
                }

                // 3) Load user by id
                Backendless.Data.of(BackendlessUser.class).findById(uid, new AsyncCallback<BackendlessUser>() {
                    @Override public void handleResponse(BackendlessUser user) { onUserReady(user); }
                    @Override public void handleFault(BackendlessFault fault) {
                        Log.w(TAG, "findById user failed: " + fault.getMessage());
                        goToSignIn();
                    }
                });
            }
            @Override public void handleFault(BackendlessFault fault) {
                Log.w(TAG, "isValidLogin failed: " + fault.getMessage());
                goToSignIn();
            }
        });
    }

    private void onUserReady(BackendlessUser user) {
        if (user == null) { goToSignIn(); return; }

        // ✅ স্টিকি সেশন
        Backendless.UserService.setCurrentUser(user);

        // Save for later launches
        prefs.edit().putString("loggedInUserId", user.getObjectId()).apply();

        // Optional: greet by name if present
        Object fn = user.getProperty("firstName");
        if (fn != null) tvWelcome.setText("Welcome back, " + fn.toString());

        teacherObjectId = user.getObjectId();
        startListeningClasses();
        attachRealtime();
    }

    private void goToSignIn() {
        Toast.makeText(this, "Please sign in", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, SignInActivity.class));
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachRealtime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bnv = findViewById(R.id.bottom_nav);
        if (bnv != null) bnv.setSelectedItemId(R.id.menu_dashboard);
    }

    // ----------------------- Data Load (Backendless) -----------------------
    private void startListeningClasses() {
        if (teacherObjectId == null || teacherObjectId.trim().isEmpty()) {
            classAdapter.submitList(new ArrayList<>());
            Toast.makeText(this, "Please sign in", Toast.LENGTH_SHORT).show();
            return;
        }

        String where = "teacherId = '" + teacherObjectId + "'";
        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause(where);
        qb.setSortBy(Collections.singletonList("created ASC"));

        Backendless.Data.of(ClassModel.class).find(qb, new AsyncCallback<List<ClassModel>>() {
            @Override
            public void handleResponse(List<ClassModel> list) {
                List<ClassModel> normalized = (list != null) ? new ArrayList<>(list) : new ArrayList<>();
                sortByCreated(normalized);
                Log.d(TAG, "Loaded classes: " + normalized.size());
                classAdapter.submitList(normalized);
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Log.e(TAG, "find classes error: " + fault.getMessage());
                Toast.makeText(TeacherDashboardActivity.this, "Failed to load classes", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sortByCreated(List<ClassModel> list) {
        try {
            list.sort(new Comparator<ClassModel>() {
                @Override public int compare(ClassModel a, ClassModel b) {
                    if (a == null || b == null) return 0;
                    if (a.getCreated() == null || b.getCreated() == null) return 0;
                    return a.getCreated().compareTo(b.getCreated());
                }
            });
        } catch (Exception ignore) {}
    }

    // ----------------------- Realtime -----------------------
    private void attachRealtime() {
        if (rtHandler != null) detachRealtime();
        rtHandler = Backendless.Data.of(ClassModel.class).rt();

        final String where = "teacherId = '" + teacherObjectId + "'";

        // Create
        rtHandler.addCreateListener(where, new AsyncCallback<ClassModel>() {
            @Override public void handleResponse(ClassModel created) {
                runOnUiThread(() -> {
                    List<ClassModel> curr = new ArrayList<>(classAdapter.getCurrentList());
                    curr.add(created);
                    sortByCreated(curr);
                    classAdapter.submitList(curr);
                });
            }
            @Override public void handleFault(BackendlessFault fault) {
                Log.e(TAG, "RT create err: " + fault.getMessage());
            }
        });

        // Update
        rtHandler.addUpdateListener(where, new AsyncCallback<ClassModel>() {
            @Override public void handleResponse(ClassModel updated) {
                runOnUiThread(() -> {
                    List<ClassModel> curr = new ArrayList<>(classAdapter.getCurrentList());
                    int idx = indexOfById(curr, getIdSafe(updated));
                    if (idx != -1) {
                        curr.set(idx, updated);
                        sortByCreated(curr);
                        classAdapter.submitList(curr);
                    } else {
                        startListeningClasses();
                    }
                });
            }
            @Override public void handleFault(BackendlessFault fault) {
                Log.e(TAG, "RT update err: " + fault.getMessage());
            }
        });

        // Delete
        rtHandler.addDeleteListener(where, new AsyncCallback<ClassModel>() {
            @Override public void handleResponse(ClassModel deleted) {
                runOnUiThread(() -> {
                    String objectId = getIdSafe(deleted);
                    List<ClassModel> curr = new ArrayList<>(classAdapter.getCurrentList());
                    int idx = indexOfById(curr, objectId);
                    if (idx != -1) {
                        curr.remove(idx);
                        classAdapter.submitList(curr);
                    } else {
                        startListeningClasses();
                    }
                });
            }
            @Override public void handleFault(BackendlessFault fault) {
                Log.e(TAG, "RT delete err: " + fault.getMessage());
            }
        });
    }

    private void detachRealtime() {
        if (rtHandler != null) {
            try {
                rtHandler.removeCreateListeners();
                rtHandler.removeUpdateListeners();
                rtHandler.removeDeleteListeners();
            } catch (Exception ignore) {}
            rtHandler = null;
        }
    }

    private static int indexOfById(List<ClassModel> list, String id) {
        if (id == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            String cur = getIdSafe(list.get(i));
            if (id.equals(cur)) return i;
        }
        return -1;
    }

    private static String getIdSafe(ClassModel m) {
        if (m == null) return null;
        try {
            return m.getId();
        } catch (Throwable t) {
            return null;
        }
    }

    // ----------------------- Bottom nav / add button -----------------------
    private void setupBottomNav() {
        BottomNavigationView bnv = findViewById(R.id.bottom_nav);
        if (bnv == null) return;

        bnv.setSelectedItemId(R.id.menu_dashboard);

        bnv.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override public boolean onNavigationItemSelected(android.view.MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.menu_dashboard) {
                    return true;
                } else if (id == R.id.menu_classes) {
                    startActivity(new Intent(TeacherDashboardActivity.this, TeacherClassesActivity.class));
                    return true;
                } else if (id == R.id.menu_attendance) {
                    Toast.makeText(TeacherDashboardActivity.this, "Attendance tapped", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.menu_profile) {
                    Toast.makeText(TeacherDashboardActivity.this, "Profile tapped", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        });
    }

    private void wireAddButton() {
        int[] candidateIds = new int[]{ R.id.btnAdd, getIdSafely("ivAdd"), getIdSafely("btnPlus") };
        for (int cid : candidateIds) {
            if (cid != 0) {
                View v = findViewById(cid);
                if (v != null) {
                    v.setOnClickListener(vw ->
                            startActivity(new Intent(TeacherDashboardActivity.this, CreateClassActivity.class))
                    );
                    break;
                }
            }
        }
    }

    private int getIdSafely(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    // ----------------------- Delete flow -----------------------
    private void confirmDelete(final ClassModel item) {
        if (item == null || getIdSafe(item) == null) {
            Toast.makeText(this, "Invalid class item", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete class?")
                .setMessage("This will remove \"" + item.getName() + "\" permanently.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> performDelete(item))
                .show();
    }

    private void performDelete(ClassModel item) {
        String objectId = getIdSafe(item);
        if (objectId == null || objectId.trim().isEmpty()) {
            Toast.makeText(this, "Invalid id", Toast.LENGTH_SHORT).show();
            return;
        }

        ClassModel toDelete = new ClassModel();
        toDelete.setId(objectId);

        Backendless.Data.of(ClassModel.class).remove(toDelete, new AsyncCallback<Long>() {
            @Override public void handleResponse(Long removed) {
                Toast.makeText(TeacherDashboardActivity.this, "Class deleted", Toast.LENGTH_SHORT).show();
                startListeningClasses();
            }
            @Override public void handleFault(BackendlessFault fault) {
                Toast.makeText(TeacherDashboardActivity.this, "Delete failed: " + fault.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ----- sample stats loaders (TODO) -----
    private void loadAttendanceStats() { /* Optional: Backendless query */ }
    private void loadAssignmentStats() { /* Optional */ }
    private void loadSubmissionStats() { /* Optional */ }
}
