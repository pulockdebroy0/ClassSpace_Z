package com.example.class_space_z;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;

import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;

public class SignInActivity extends AppCompatActivity {

    private static final String TAG = "SIGN_IN";

    private EditText etEmail, etPassword;
    private Button btnSignIn;
    private TextView tvForgotPassword, tvSignUp;

    // Role toggle
    private TextView btnTeacher, btnStudent;
    private String selectedRole = "TEACHER"; // default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        etEmail          = findViewById(R.id.etEmail);
        etPassword       = findViewById(R.id.etPassword);
        btnSignIn        = findViewById(R.id.btnSignIn);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvSignUp         = findViewById(R.id.tvSignUp);

        btnTeacher = findViewById(R.id.btnTeacher);
        btnStudent = findViewById(R.id.btnStudent);

        setupRoleToggle();

        btnSignIn.setOnClickListener(v -> signInUser());
        tvForgotPassword.setOnClickListener(v -> resetPassword());
        tvSignUp.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));
    }

    // ❌ onStart() auto-route এখানে দরকার নেই (SplashActivity-তেই হবে)
    // @Override protected void onStart() { ... }

    // ---------- Role Toggle ----------
    private void setupRoleToggle() {
        applyRoleUI("TEACHER");

        btnTeacher.setOnClickListener(v -> {
            selectedRole = "TEACHER";
            applyRoleUI(selectedRole);
        });

        btnStudent.setOnClickListener(v -> {
            selectedRole = "STUDENT";
            applyRoleUI(selectedRole);
        });
    }

    private void applyRoleUI(String role) {
        boolean isTeacher = "TEACHER".equalsIgnoreCase(role);

        if (isTeacher) {
            setSafeBackground(btnTeacher, getResId("toggle_left_selected", "drawable"));
            btnTeacher.setTextColor(0xFFFFFFFF);
            setSafeBackground(btnStudent, getResId("toggle_right_unselected", "drawable"));
            btnStudent.setTextColor(0xFF000000);
        } else {
            setSafeBackground(btnTeacher, getResId("toggle_left_unselected", "drawable"));
            btnTeacher.setTextColor(0xFF000000);
            setSafeBackground(btnStudent, getResId("toggle_right_selected", "drawable"));
            btnStudent.setTextColor(0xFFFFFFFF);
        }
    }

    private int getResId(String name, String defType) {
        return getResources().getIdentifier(name, defType, getPackageName());
    }

    private void setSafeBackground(TextView view, @DrawableRes int resId) {
        if (resId != 0) view.setBackgroundResource(resId);
    }

    // ---------- Auth flow ----------
    private void signInUser() {
        hideKeyboard(); // ✅ UX tweak

        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) { // ✅ min length check
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSignIn.setEnabled(false);

        Backendless.UserService.login(email, password, new AsyncCallback<BackendlessUser>() {
            @Override
            public void handleResponse(BackendlessUser user) {
                btnSignIn.setEnabled(true);

                if (user == null) {
                    Toast.makeText(SignInActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                // (Optional) Block unconfirmed emails if your Console requires confirmation
                Object ec = user.getProperty("emailConfirmed");
                if (ec instanceof Boolean && !(Boolean) ec) {
                    Toast.makeText(SignInActivity.this, "Please confirm your email first.", Toast.LENGTH_LONG).show();
                    Backendless.UserService.logout(new AsyncCallback<Void>() {
                        @Override public void handleResponse(Void response) {}
                        @Override public void handleFault(BackendlessFault fault) {}
                    });
                    return;
                }

                // Validate role (missing + mismatch → logout)
                String role = "";
                Object roleObj = user.getProperty("role");
                if (roleObj != null) role = String.valueOf(roleObj);

                if (TextUtils.isEmpty(role)) {
                    Toast.makeText(SignInActivity.this, "Role not assigned to this account!", Toast.LENGTH_LONG).show();
                    Backendless.UserService.logout(new AsyncCallback<Void>() {
                        @Override public void handleResponse(Void response) {}
                        @Override public void handleFault(BackendlessFault fault) {}
                    });
                    return;
                }
                if (!role.equalsIgnoreCase(selectedRole)) {
                    Toast.makeText(SignInActivity.this, "Selected role doesn't match your account role.", Toast.LENGTH_LONG).show();
                    Backendless.UserService.logout(new AsyncCallback<Void>() {
                        @Override public void handleResponse(Void response) {}
                        @Override public void handleFault(BackendlessFault fault) {}
                    });
                    return;
                }

                // Save user id to reuse later (for dashboards)
                getSharedPreferences("TeacherPrefs", MODE_PRIVATE)
                        .edit()
                        .putString("loggedInUserId", user.getObjectId())
                        .apply();

                Toast.makeText(SignInActivity.this, "✅ Login successful!", Toast.LENGTH_SHORT).show();
                routeAfterLogin(user);
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                btnSignIn.setEnabled(true);
                // ✅ sanitize user-facing message
                Toast.makeText(SignInActivity.this,
                        "❌ Login failed. Please check your email/password and try again.",
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, "Login error: " + fault.getMessage());
            }
        }, true); // true = keep logged in (persistent session)
    }

    private void resetPassword() {
        String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Enter email to reset password", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email", Toast.LENGTH_SHORT).show();
            return;
        }

        Backendless.UserService.restorePassword(email, new AsyncCallback<Void>() {
            @Override
            public void handleResponse(Void response) {
                Toast.makeText(SignInActivity.this, "📩 Reset email sent successfully.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Toast.makeText(SignInActivity.this, "❌ Failed to send reset email.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Reset error: " + fault.getMessage());
            }
        });
    }

    // ---------- Helpers ----------
    private void routeAfterLogin(BackendlessUser user) {
        if (user == null) return;

        String role = "";
        Object roleObj = user.getProperty("role");
        if (roleObj != null) role = roleObj.toString();

        Intent next = "TEACHER".equalsIgnoreCase(role)
                ? new Intent(SignInActivity.this, TeacherDashboardActivity.class)
                : new Intent(SignInActivity.this, StudentDashboardActivity.class);

        startActivity(next);
        finish();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
            getWindow().getDecorView().clearFocus();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selectedRole", selectedRole);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String role = savedInstanceState.getString("selectedRole", "TEACHER");
        selectedRole = role;
        applyRoleUI(role);
    }
}
