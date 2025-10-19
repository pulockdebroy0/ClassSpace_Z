package com.example.class_space_z;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.backendless.Backendless;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;
import com.backendless.persistence.DataQueryBuilder;
import com.backendless.rt.data.EventHandler;
import com.example.class_space_z.adapters.SourceAdapter;
import com.example.class_space_z.adapters.SubmissionAdapter;
import com.example.class_space_z.models.SourceItem;
import com.example.class_space_z.models.SubmissionItem;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assignment view backed by Backendless RT Data
 *
 * Schema (recommended)
 * Tables:
 *  - Classes { objectId, name, ... }
 *  - Assignments { objectId, title, reportUrl, grade, feedback, crossStudentMatches:Int, gradedAt:Date, gradedBy, class:Relation->Classes }
 *  - Submissions { objectId, assignmentId:String, studentName, fileName, fileUrl, similarity:Double, submittedAt:Date }
 *  - MatchedSources { objectId, assignmentId:String, title, domain, url, matchPercent:Double }
 */
public class AssignmentDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_ASSIGNMENT_ID = "assignmentId";

    // --- Tuning for plagiarism summary ---
    private static final int FLAG_THRESHOLD = 40; // % or higher => flagged
    private static final int HIGH_SIMILARITY  = 70;
    private static final int MED_SIMILARITY   = 40;

    // UI
    private RecyclerView rvSubmissions, rvMatchedSources;
    private EditText etGrade, etFeedback;
    private Button btnSaveFeedback, btnDownloadReport;
    private ImageButton btnBack;
    private TextView tvFlaggedText, tvCrossStudentMatches;

    // Data lists
    private final List<SubmissionItem> submissionList = new ArrayList<>();
    private final List<SourceItem> sourcesList = new ArrayList<>();

    // Adapters
    private SubmissionAdapter submissionAdapter;
    private SourceAdapter sourceAdapter;

    // Routing
    private String classId;
    private String className;
    private String assignmentId;

    // Cached reportUrl/cross matches for header
    private String reportUrlCached = "";
    private int crossMatchesCached = 0;

    // Realtime handlers
    private EventHandler<Map> assignmentRT;
    private EventHandler<Map> submissionsRT;
    private EventHandler<Map> sourcesRT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assignment_details);

        // ---- Init Backendless (recommended in Application.onCreate) ----
        Backendless.initApp(
                getApplicationContext(),
                "8ACC0B61-36C3-499C-98EF-F7E8E78FFD19",
                "3C5A55CA-E265-4AB3-B857-F0C0DD98F380"
        );

        // ---- Read extras ----
        Intent in = getIntent();
        classId      = safe(in.getStringExtra(TeacherClassDetailsActivity.EXTRA_CLASS_ID));
        className    = safe(in.getStringExtra(TeacherClassDetailsActivity.EXTRA_CLASS_NAME));
        assignmentId = safe(in.getStringExtra(EXTRA_ASSIGNMENT_ID));
        if (className.isEmpty()) className = "Class";
        if (assignmentId.isEmpty()) assignmentId = "assignment_demo"; // ensure caller passes real id
        setTitle(className + " • Assignment");

        // ---- Bind UI ----
        rvSubmissions         = findViewById(R.id.rvSubmissions);
        rvMatchedSources      = findViewById(R.id.rvMatchedSources);
        etGrade               = findViewById(R.id.etGrade);
        etFeedback            = findViewById(R.id.etFeedback);
        btnSaveFeedback       = findViewById(R.id.btnSaveFeedback);
        btnDownloadReport     = findViewById(R.id.btnDownloadReport);
        btnBack               = findViewById(R.id.btnBack);
        tvFlaggedText         = findViewById(R.id.tvFlaggedText);
        tvCrossStudentMatches = findViewById(R.id.tvCrossStudentMatches);

        // ---- RecyclerViews ----
        if (rvSubmissions != null) {
            rvSubmissions.setLayoutManager(new LinearLayoutManager(this));
            submissionAdapter = new SubmissionAdapter(submissionList, item -> openUrl(item.getFileUrl()));
            rvSubmissions.setAdapter(submissionAdapter);
        }
        if (rvMatchedSources != null) {
            rvMatchedSources.setLayoutManager(new LinearLayoutManager(this));
            sourceAdapter = new SourceAdapter(sourcesList, item -> openUrl(item.getUrl()));
            rvMatchedSources.setAdapter(sourceAdapter);
        }

        // ---- Button handlers ----
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (btnSaveFeedback != null) btnSaveFeedback.setOnClickListener(v -> saveFeedback());
        if (btnDownloadReport != null) btnDownloadReport.setOnClickListener(v -> downloadReport());

        setupBottomNav();

        // Initial load
        refreshAll();
    }

    private void refreshAll() {
        loadAssignment();
        loadSubmissions();
        loadSources();
    }

    // ------------------ Backendless loads ------------------

    private void loadAssignment() {
        Backendless.Data.of("Assignments").findById(assignmentId, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map assignment) {
                if (assignment == null) return;
                String grade = safe((String) assignment.get("grade"));
                String feedback = safe((String) assignment.get("feedback"));
                Object cm = assignment.get("crossStudentMatches");
                reportUrlCached = safe((String) assignment.get("reportUrl"));
                crossMatchesCached = (cm instanceof Number) ? ((Number) cm).intValue() : 0;

                if (etGrade != null) {
                    String cur = safe(etGrade.getText() == null ? "" : etGrade.getText().toString());
                    if (!grade.equals(cur)) etGrade.setText(grade);
                }
                if (etFeedback != null) {
                    String cur = safe(etFeedback.getText() == null ? "" : etFeedback.getText().toString());
                    if (!feedback.equals(cur)) etFeedback.setText(feedback);
                }
                if (tvCrossStudentMatches != null) {
                    tvCrossStudentMatches.setText("Cross-student matches: " + crossMatchesCached);
                }
            }
            @Override public void handleFault(BackendlessFault fault) {
                toast("Load assignment failed: " + fault.getMessage());
            }
        });
    }

    private void loadSubmissions() {
        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause("assignmentId='" + assignmentId + "'");
        qb.setSortBy("submittedAt DESC");
        qb.setPageSize(100);

        Backendless.Data.of("Submissions").find(qb, new AsyncCallback<List<Map>>() {
            @Override public void handleResponse(List<Map> result) {
                submissionList.clear();
                if (result != null) {
                    for (Map m : result) {
                        String studentName = safe((String) m.get("studentName"));
                        String fileName    = safe((String) m.get("fileName"));
                        String fileUrl     = safe((String) m.get("fileUrl"));
                        double similarity  = (m.get("similarity") instanceof Number) ? ((Number) m.get("similarity")).doubleValue() : 0.0;
                        Date submittedAt   = (Date) m.get("submittedAt");
                        submissionList.add(new SubmissionItem(
                                studentName,
                                fileName,
                                fileUrl,
                                similarity,
                                submittedAt == null ? null : new com.google.firebase.Timestamp(submittedAt.getTime()/1000, 0)
                        ));
                    }
                }
                if (submissionAdapter != null) submissionAdapter.notifyDataSetChanged();
            }
            @Override public void handleFault(BackendlessFault fault) {
                toast("Load submissions failed: " + fault.getMessage());
            }
        });
    }

    private void loadSources() {
        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause("assignmentId='" + assignmentId + "'");
        qb.setSortBy("matchPercent DESC");
        qb.setPageSize(100);

        Backendless.Data.of("MatchedSources").find(qb, new AsyncCallback<List<Map>>() {
            @Override public void handleResponse(List<Map> result) {
                sourcesList.clear();
                if (result != null) {
                    for (Map m : result) {
                        String title   = safe((String) m.get("title"));
                        String domain  = safe((String) m.get("domain"));
                        String url     = safe((String) m.get("url"));
                        double percent = (m.get("matchPercent") instanceof Number) ? ((Number) m.get("matchPercent")).doubleValue() : 0.0;
                        sourcesList.add(new SourceItem(title, domain, url, percent));
                    }
                }
                if (sourceAdapter != null) sourceAdapter.notifyDataSetChanged();
                updatePlagiarismHeader();
            }
            @Override public void handleFault(BackendlessFault fault) {
                toast("Load sources failed: " + fault.getMessage());
            }
        });
    }

    // ------------------ Header summaries ------------------

    private void updatePlagiarismHeader() {
        if (tvFlaggedText == null) return;

        if (sourcesList.isEmpty()) {
            tvFlaggedText.setText("No flagged sections");
            return;
        }

        int flagged = 0;
        double sum = 0;
        int max = 0;
        for (SourceItem s : sourcesList) {
            int p = (int) Math.round(s.getMatchPercent());
            sum += p;
            if (p > max) max = p;
            if (p >= FLAG_THRESHOLD) flagged++;
        }
        int avg = (int) Math.round(sum / sourcesList.size());

        String severity = (max >= HIGH_SIMILARITY) ? "High"
                : (max >= MED_SIMILARITY) ? "Medium" : "Low";

        tvFlaggedText.setText(
                "Flagged sections: " + flagged +
                        " • Avg match ~ " + avg + "%" +
                        " • Peak: " + max + "% (" + severity + ")"
        );
    }

    // ------------------ Actions ------------------

    private void downloadReport() {
        if (!safe(reportUrlCached).isEmpty()) {
            openUrl(reportUrlCached);
            return;
        }
        // fallback: fetch once
        loadAssignment();
        if (!safe(reportUrlCached).isEmpty()) openUrl(reportUrlCached);
    }

    private void saveFeedback() {
        String grade = etGrade != null ? safe(etGrade.getText().toString()) : "";
        String feedback = etFeedback != null ? safe(etFeedback.getText().toString()) : "";

        if (grade.isEmpty() || feedback.isEmpty()) {
            Toast.makeText(this, "Please enter both grade and feedback", Toast.LENGTH_SHORT).show();
            return;
        }

        Backendless.Data.of("Assignments").findById(assignmentId, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map assignment) {
                if (assignment == null) assignment = new HashMap();
                assignment.put("objectId", assignmentId);
                assignment.put("grade", grade);
                assignment.put("feedback", feedback);
                assignment.put("gradedAt", new Date());
                // assignment.put("gradedBy", currentUserIdOrEmail);

                Backendless.Data.of("Assignments").save(assignment, new AsyncCallback<Map>() {
                    @Override public void handleResponse(Map saved) {
                        Toast.makeText(AssignmentDetailsActivity.this, "Saved to Backendless", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void handleFault(BackendlessFault fault) {
                        Toast.makeText(AssignmentDetailsActivity.this, "Backendless error: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void handleFault(BackendlessFault fault) {
                Toast.makeText(AssignmentDetailsActivity.this, "Load failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------ REALTIME ------------------

    private void subscribeRealtime() {
        // Assignments: update listener for this one assignment
        assignmentRT = Backendless.Data.of("Assignments").rt();
        String whereAssignment = "objectId='" + assignmentId + "'";
        assignmentRT.addUpdateListener(whereAssignment, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                // Just refresh the header fields
                runOnUiThread(AssignmentDetailsActivity.this::loadAssignment);
            }
            @Override public void handleFault(BackendlessFault fault) { /* log if needed */ }
        });

        // Submissions: create/update/delete for this assignmentId
        submissionsRT = Backendless.Data.of("Submissions").rt();
        String whereSub = "assignmentId='" + assignmentId + "'";
        submissionsRT.addCreateListener(whereSub, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                runOnUiThread(AssignmentDetailsActivity.this::loadSubmissions);
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });
        submissionsRT.addUpdateListener(whereSub, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                runOnUiThread(AssignmentDetailsActivity.this::loadSubmissions);
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });
        submissionsRT.addDeleteListener(whereSub, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                runOnUiThread(AssignmentDetailsActivity.this::loadSubmissions);
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });

        // MatchedSources: create/update/delete for this assignmentId
        sourcesRT = Backendless.Data.of("MatchedSources").rt();
        String whereSrc = "assignmentId='" + assignmentId + "'";
        sourcesRT.addCreateListener(whereSrc, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                runOnUiThread(AssignmentDetailsActivity.this::loadSources);
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });
        sourcesRT.addUpdateListener(whereSrc, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                runOnUiThread(AssignmentDetailsActivity.this::loadSources);
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });
        sourcesRT.addDeleteListener(whereSrc, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                runOnUiThread(AssignmentDetailsActivity.this::loadSources);
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });
    }

    private void unsubscribeRealtime() {
        try {
            if (assignmentRT != null) {
                assignmentRT.removeUpdateListeners();
                assignmentRT = null;
            }
            if (submissionsRT != null) {
                submissionsRT.removeCreateListeners();
                submissionsRT.removeUpdateListeners();
                submissionsRT.removeDeleteListeners();
                submissionsRT = null;
            }
            if (sourcesRT != null) {
                sourcesRT.removeCreateListeners();
                sourcesRT.removeUpdateListeners();
                sourcesRT.removeDeleteListeners();
                sourcesRT = null;
            }
        } catch (Exception ignore) { }
    }

    // ------------------ Nav / Utils ------------------

    private void setupBottomNav() {
        View bottom = findViewById(getId("bottomNav"));
        if (bottom == null) bottom = findViewById(getId("bottom_nav"));
        if (bottom == null) return;

        // Case A: custom containers
        View vHome   = bottom.findViewById(getId("navHome"));
        View vAssign = bottom.findViewById(getId("navAssignments"));
        View vProf   = bottom.findViewById(getId("navProfile"));

        boolean handled = false;
        if (vHome != null)   { vHome.setOnClickListener(v -> toast("Go Home")); handled = true; }
        if (vAssign != null) { vAssign.setOnClickListener(v -> toast("Assignments")); handled = true; }
        if (vProf != null)   { vProf.setOnClickListener(v -> toast("Profile")); handled = true; }
        if (handled) return;

        // Case B: BottomNavigationView
        if (bottom instanceof BottomNavigationView) {
            BottomNavigationView bnv = (BottomNavigationView) bottom;
            final int menuHome = getId("menu_home");
            final int menuAssignments = getId("menu_assignments");
            final int menuProfile = getId("menu_profile");

            bnv.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (menuHome != 0 && id == menuHome) { toast("Go Home"); return true; }
                if (menuAssignments != 0 && id == menuAssignments) { toast("Assignments"); return true; }
                if (menuProfile != 0 && id == menuProfile) { toast("Profile"); return true; }
                toast("Clicked"); return true;
            });
        }
    }

    private String safe(@Nullable String s) {
        return (s == null) ? "" : s.trim();
    }

    private void openUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "No link available", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show();
        }
    }

    private int getId(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        subscribeRealtime();   // ✅ start RT
    }

    @Override
    protected void onPause() {
        super.onPause();
        unsubscribeRealtime(); // ✅ stop RT
    }
}
