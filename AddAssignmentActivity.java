// AddAssignmentActivity.java
package com.example.class_space_z;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.backendless.Backendless;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;
import com.backendless.persistence.DataQueryBuilder;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddAssignmentActivity extends AppCompatActivity {

    public static final String EXTRA_ASSIGNMENT_ID = "assignmentId";

    private TextInputLayout tilTitle, tilBatch, tilSection, tilDue;
    private TextInputEditText etTitle, etBatch, etSection, etDue;

    private long dueMillis = 0L;         // ✅ datepicker থেকে নেয়া due date (millis)
    private boolean saving = false;      // ✅ ডাবল-ক্লিক গার্ড

    private String assignmentId = null;  // ✅ update mode হলে লাগবে

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_assignment);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        if (tb != null) tb.setNavigationOnClickListener(v -> finish());

        tilTitle   = findViewById(R.id.tilTitle);
        tilBatch   = findViewById(R.id.tilBatch);
        tilSection = findViewById(R.id.tilSection);
        tilDue     = findViewById(R.id.tilDue);

        etTitle   = findViewById(R.id.etTitle);
        etBatch   = findViewById(R.id.etBatch);
        etSection = findViewById(R.id.etSection);
        etDue     = findViewById(R.id.etDue);

        if (etDue != null) etDue.setOnClickListener(v -> openDatePicker());
        if (tilDue != null) tilDue.setEndIconOnClickListener(v -> openDatePicker());

        findViewById(R.id.btnSave).setOnClickListener(v -> save());

        // 🔹 Update mode (optional)
        assignmentId = getIntent().getStringExtra(EXTRA_ASSIGNMENT_ID);
        if (!TextUtils.isEmpty(assignmentId)) {
            setTitle("Edit Assignment");
            preloadAssignment(assignmentId);
        } else {
            setTitle("Add Assignment");
        }
    }

    private void openDatePicker() {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select due date")
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            dueMillis = selection != null ? selection : 0L;
            String date = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    .format(new Date(dueMillis));
            if (etDue != null) etDue.setText(date);
        });
        picker.show(getSupportFragmentManager(), "due_picker");
    }

    // ---------- Load existing (for edit mode) ----------
    private void preloadAssignment(String id) {
        Backendless.Data.of("Assignments").findById(id, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map obj) {
                if (obj == null) return;
                String title   = s(obj.get("title"));
                String batch   = s(obj.get("batch"));
                String section = s(obj.get("section"));
                Long   dueMs   = obj.get("dueMillis") instanceof Number ? ((Number) obj.get("dueMillis")).longValue() : 0L;
                String dueText = s(obj.get("dueText"));

                if (etTitle != null)   etTitle.setText(title);
                if (etBatch != null)   etBatch.setText(batch);
                if (etSection != null) etSection.setText(section);

                if (dueMs != null && dueMs > 0) {
                    dueMillis = dueMs;
                    if (etDue != null) etDue.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            .format(new Date(dueMillis)));
                } else if (!TextUtils.isEmpty(dueText) && etDue != null) {
                    etDue.setText(dueText);
                }
            }
            @Override public void handleFault(BackendlessFault fault) {
                Toast.makeText(AddAssignmentActivity.this, "Load failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void save() {
        if (saving) return;

        String title   = etTitle != null && etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String batch   = etBatch != null && etBatch.getText() != null ? etBatch.getText().toString().trim() : "";
        String section = etSection != null && etSection.getText() != null ? etSection.getText().toString().trim() : "";
        String dueText = etDue != null && etDue.getText() != null ? etDue.getText().toString().trim() : "";

        // clear errors
        if (tilTitle != null)   tilTitle.setError(null);
        if (tilBatch != null)   tilBatch.setError(null);
        if (tilSection != null) tilSection.setError(null);
        if (tilDue != null)     tilDue.setError(null);

        // validate
        if (TextUtils.isEmpty(title))   { if (tilTitle != null)   tilTitle.setError("Required");   return; }
        if (TextUtils.isEmpty(batch))   { if (tilBatch != null)   tilBatch.setError("Required");   return; }
        if (TextUtils.isEmpty(section)) { if (tilSection != null) tilSection.setError("Required"); return; }
        if (TextUtils.isEmpty(dueText)) { if (tilDue != null)     tilDue.setError("Required");     return; }

        saving = true;
        findViewById(R.id.btnSave).setEnabled(false);

        // teacherId/prefs থেকে আনো—ডেমো হিসেবে placeholder
        String teacherId = "teacher_local"; // TODO: Backendless.UserService.getCurrentUser() থেকে আনো

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("batch", batch);
        data.put("section", section);
        data.put("dueText", dueText);
        data.put("dueMillis", dueMillis > 0 ? dueMillis : System.currentTimeMillis());
        data.put("teacherId", teacherId);
        data.put("createdAt", new Date()); // Backendless auto হলেও fallback ভালো

        if (TextUtils.isEmpty(assignmentId)) {
            // CREATE
            Backendless.Data.of("Assignments").save(data, new AsyncCallback<Map>() {
                @Override public void handleResponse(Map saved) {
                    Toast.makeText(AddAssignmentActivity.this, "Assignment added", Toast.LENGTH_SHORT).show();

                    // চাইলে caller-কে data ফেরত দিতে পারো:
                    getIntent().putExtra("title", title);
                    getIntent().putExtra("batch", batch);
                    getIntent().putExtra("section", section);
                    getIntent().putExtra("due", dueText);
                    getIntent().putExtra("dueMillis", (long) data.get("dueMillis"));
                    setResult(RESULT_OK, getIntent());

                    finish();
                }
                @Override public void handleFault(BackendlessFault fault) {
                    saving = false;
                    findViewById(R.id.btnSave).setEnabled(true);
                    Toast.makeText(AddAssignmentActivity.this, "Save failed: " + fault.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            // UPDATE (by id)
            Backendless.Data.of("Assignments").findById(assignmentId, new AsyncCallback<Map>() {
                @Override public void handleResponse(Map obj) {
                    if (obj == null) {
                        saving = false;
                        findViewById(R.id.btnSave).setEnabled(true);
                        Toast.makeText(AddAssignmentActivity.this, "Not found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    obj.putAll(data);
                    Backendless.Data.of("Assignments").save(obj, new AsyncCallback<Map>() {
                        @Override public void handleResponse(Map saved) {
                            Toast.makeText(AddAssignmentActivity.this, "Assignment updated", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        }
                        @Override public void handleFault(BackendlessFault fault) {
                            saving = false;
                            findViewById(R.id.btnSave).setEnabled(true);
                            Toast.makeText(AddAssignmentActivity.this, "Update failed: " + fault.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override public void handleFault(BackendlessFault fault) {
                    saving = false;
                    findViewById(R.id.btnSave).setEnabled(true);
                    Toast.makeText(AddAssignmentActivity.this, "Load failed: " + fault.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private static String s(Object o) { return o == null ? "" : String.valueOf(o); }
}
