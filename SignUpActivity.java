package com.example.class_space_z;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

// Backendless
import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;

public class SignUpActivity extends AppCompatActivity {

    private TextView btnTeacher, btnStudent, tvLogin;
    private MaterialButton btnSignUp;
    private EditText etEmail, etFirstName, etLastName, etPassword, etConfirmPassword;

    private String role = "TEACHER"; // default
    private static final String KEY_ROLE = "key_role";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        btnTeacher = findViewById(R.id.btnTeacher);
        btnStudent = findViewById(R.id.btnStudent);
        btnSignUp  = findViewById(R.id.btnSignUp);
        tvLogin    = findViewById(R.id.tvLogin);

        etEmail           = findViewById(R.id.etEmail);
        etFirstName       = findViewById(R.id.etFirstName);
        etLastName        = findViewById(R.id.etLastName);
        etPassword        = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        if (savedInstanceState != null) {
            role = savedInstanceState.getString(KEY_ROLE, "TEACHER");
        }
        updateToggle(role);

        // Toggle listeners
        btnTeacher.setOnClickListener(v -> {
            role = "TEACHER";
            updateToggle(role);
        });
        btnStudent.setOnClickListener(v -> {
            role = "STUDENT";
            updateToggle(role);
        });

        // Actions
        btnSignUp.setOnClickListener(v -> registerUser());
        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, SignInActivity.class));
            finish();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_ROLE, role);
    }

    /** Update UI for current role selection */
    private void updateToggle(String currentRole) {
        int white = ContextCompat.getColor(this, android.R.color.white);
        int black = ContextCompat.getColor(this, android.R.color.black);
        boolean isTeacher = "TEACHER".equalsIgnoreCase(currentRole);

        btnTeacher.setBackgroundResource(
                isTeacher ? R.drawable.toggle_left_selected : R.drawable.toggle_left_unselected);
        btnStudent.setBackgroundResource(
                isTeacher ? R.drawable.toggle_right_unselected : R.drawable.toggle_right_selected);

        btnTeacher.setTextColor(isTeacher ? white : black);
        btnStudent.setTextColor(isTeacher ? black : white);

        btnTeacher.setSelected(isTeacher);
        btnStudent.setSelected(!isTeacher);
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // ---- Basic validation ----
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(firstName) ||
                TextUtils.isEmpty(lastName) || TextUtils.isEmpty(password) ||
                TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, "⚠ Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "⚠ Enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "⚠ Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "⚠ Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        // ---- Backendless registration ----
        BackendlessUser bu = new BackendlessUser();
        bu.setEmail(email);
        bu.setPassword(password);
        bu.setProperty("firstName", firstName);
        bu.setProperty("lastName", lastName);
        bu.setProperty("role", role); // custom property in Users table

        btnSignUp.setEnabled(false);

        Backendless.UserService.register(bu, new AsyncCallback<BackendlessUser>() {
            @Override
            public void handleResponse(BackendlessUser response) {
                btnSignUp.setEnabled(true);

                // If "Enable Email Confirmation" is ON in Backendless console,
                // an email is sent automatically.
                Toast.makeText(SignUpActivity.this,
                        "✅ Account created. Please check your email for verification (if enabled).",
                        Toast.LENGTH_LONG).show();

                // Go to Sign In
                startActivity(new Intent(SignUpActivity.this, SignInActivity.class));
                finish();
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                btnSignUp.setEnabled(true);
                Toast.makeText(SignUpActivity.this,
                        "❌ Sign Up Failed: " + fault.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
