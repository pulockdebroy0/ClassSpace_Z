package com.example.class_space_z;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;
import com.example.class_space_z.models.ClassModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Random;

public class CreateClassActivity extends AppCompatActivity {

    private TextInputEditText etName, etBatch, etSection, etStudents;
    private MaterialButton btnCreate, btnCancel;
    private ProgressBar progress;

    private String userId; // ✅ session-safe cache

    private static final String TAG = "CreateClass";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_class);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        etName     = findViewById(R.id.etName);
        etBatch    = findViewById(R.id.etBatch);
        etSection  = findViewById(R.id.etSection);
        etStudents = findViewById(R.id.etStudents);
        btnCreate  = findViewById(R.id.btnCreate);
        btnCancel  = findViewById(R.id.btnClose);
        progress   = findViewById(R.id.progress);

        toggleLoading(false);

        btnCreate.setOnClickListener(v -> {
            hideKeyboard();
            saveClass();
        });
        btnCancel.setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // ✅ Session verify + restore (more robust)
        Backendless.UserService.isValidLogin(new AsyncCallback<Boolean>() {
            @Override public void handleResponse(Boolean ok) {
                if (!Boolean.TRUE.equals(ok)) {
                    redirectToSignIn();
                    return;
                }
                BackendlessUser cu = Backendless.UserService.CurrentUser();
                if (cu != null) {
                    userId = cu.getObjectId();
                    Log.d(TAG, "isValid=true, CurrentUser OK: " + userId);
                    return;
                }
                // fallback to loggedInUser()
                String uid = Backendless.UserService.loggedInUser();
                if (!TextUtils.isEmpty(uid)) {
                    userId = uid;
                    Log.d(TAG, "isValid=true, loggedInUser(): " + userId);
                    return;
                }
                // last resort: ask sign-in
                redirectToSignIn();
            }
            @Override public void handleFault(BackendlessFault fault) {
                Log.w(TAG, "isValidLogin fault: " + fault.getMessage());
                redirectToSignIn();
            }
        });
    }

    private void redirectToSignIn() {
        Toast.makeText(this, "Please sign in", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, SignInActivity.class));
        // এখানে finish() দিচ্ছি না—যাতে সাইন-ইন শেষে ব্যাক করলে এই পেজে আসতে পারে
    }

    private void saveClass() {
        String name    = safeText(etName);
        String batch   = safeText(etBatch);
        String section = safeText(etSection);
        int students   = safeInt(safeText(etStudents));

        // ✅ basic validations
        if (TextUtils.isEmpty(name))    { etName.setError("Required");    etName.requestFocus();    return; }
        if (TextUtils.isEmpty(batch))   { etBatch.setError("Required");   etBatch.requestFocus();   return; }
        if (TextUtils.isEmpty(section)) { etSection.setError("Required"); etSection.requestFocus(); return; }
        if (students <= 0)              { etStudents.setError("Enter valid number"); etStudents.requestFocus(); return; }

        // ✅ userId missing? try one-shot fast recovery before bailing
        if (TextUtils.isEmpty(userId)) {
            BackendlessUser cu = Backendless.UserService.CurrentUser();
            if (cu != null) userId = cu.getObjectId();
            if (TextUtils.isEmpty(userId)) userId = Backendless.UserService.loggedInUser();
        }
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(this, "Not signed in!", Toast.LENGTH_SHORT).show();
            redirectToSignIn();
            return;
        }

        toggleLoading(true);

        // Build model
        ClassModel model = new ClassModel();
        model.setName(name);
        // ⚠️ নিশ্চিত করো এগুলোর setter তোমার ClassModel-এ আছে
        // (backend টেবিলেও এই ফিল্ডগুলো থাকতে হবে)
        model.setBatch(batch);
        model.setSection(section);
        model.setStudents(students);
        model.setTeacherId(userId);
        model.setInviteCode(genInviteCode());
        model.setAttendancePercentage(0);
        model.setPendingAssignments(0);
        model.setNewSubmissions(0);

        Log.d(TAG, "save start teacherId=" + userId);

        Backendless.Data.of(ClassModel.class).save(model, new AsyncCallback<ClassModel>() {
            @Override
            public void handleResponse(ClassModel saved) {
                toggleLoading(false);

                // ✅ নাল-সেফ objectId resolve
                String cid = null;
                try {
                    cid = saved != null ? (saved.getId() != null ? saved.getId() : saved.getObjectId()) : null;
                } catch (Throwable ignore) {}

                Log.d(TAG, "save success id=" + cid);
                Toast.makeText(CreateClassActivity.this, "Class created", Toast.LENGTH_SHORT).show();

                // details অথবা লিস্ট—এখানে details এ নিলাম
                Intent it = new Intent(CreateClassActivity.this, TeacherClassDetailsActivity.class);
                if (!TextUtils.isEmpty(cid)) {
                    it.putExtra(TeacherClassDetailsActivity.EXTRA_CLASS_ID,   cid);
                }
                it.putExtra(TeacherClassDetailsActivity.EXTRA_CLASS_NAME, saved != null ? saved.getName() : name);
                startActivity(it);

                finish();
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                toggleLoading(false);
                String msg = (fault != null && !TextUtils.isEmpty(fault.getMessage()))
                        ? fault.getMessage() : "Unknown error";
                Toast.makeText(CreateClassActivity.this, "Create failed: " + msg, Toast.LENGTH_LONG).show();
                Log.e(TAG, "save fail: " + msg);
            }
        });
    }

    private void toggleLoading(boolean show) {
        if (progress != null) progress.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        if (btnCreate != null) btnCreate.setEnabled(!show); // ✅ double-tap guard
        if (btnCancel != null) btnCancel.setEnabled(!show);
    }

    private String safeText(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    private int safeInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private String genInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (getCurrentFocus() != null && imm != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception ignore) {}
    }
}
