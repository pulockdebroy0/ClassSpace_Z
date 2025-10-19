package com.example.class_space_z;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName, tvEmail;
    private Button btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvName = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);
        btnLogout = findViewById(R.id.btnLogout);

        // 🔹 Load current user info
        BackendlessUser user = Backendless.UserService.CurrentUser();
        if (user != null) {
            String name = (String) user.getProperty("name");
            String email = user.getEmail();

            tvName.setText(name != null ? name : "No Name");
            tvEmail.setText(email != null ? email : "No Email");
        } else {
            tvName.setText("Guest User");
            tvEmail.setText("Not signed in");
        }

        // 🔹 Logout button action
        btnLogout.setOnClickListener(v -> {
            Backendless.UserService.logout(new AsyncCallback<Void>() {
                @Override
                public void handleResponse(Void response) {
                    Toast.makeText(ProfileActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(ProfileActivity.this, SignInActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                }

                @Override
                public void handleFault(BackendlessFault fault) {
                    Toast.makeText(ProfileActivity.this, "Logout failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
