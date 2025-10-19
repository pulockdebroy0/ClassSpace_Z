package com.example.class_space_z;

import android.app.Activity;             // ✅ AppCompatActivity → Activity
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;

import java.util.concurrent.atomic.AtomicBoolean;

public class SplashActivity extends Activity {

    private static final String APP_ID  = "8ACC0B61-36C3-499C-98EF-F7E8E78FFD19";
    private static final String API_KEY = "3C5A55CA-E265-4AB3-B857-F0C0DD98F380";

    private static final long MIN_SPLASH_MS = 450L;
    private static final long HARD_TIMEOUT_MS = 6000L;

    private long startedAt;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean didNavigate = new AtomicBoolean(false);

    private final Runnable timeoutFallback = () -> safeGo(SignInActivity.class);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ❌ getSupportActionBar().hide() দরকার নেই (Activity তে নেই)
        setContentView(R.layout.activity_splash);

        startedAt = System.currentTimeMillis();

        // Backendless init (best: App.java তে; না থাকলে এখানে)
        Backendless.initApp(getApplicationContext(), APP_ID, API_KEY);

        // হার্ড-টাইমআউট
        main.postDelayed(timeoutFallback, HARD_TIMEOUT_MS);

        route();
    }

    private void route() {
        Backendless.UserService.isValidLogin(new AsyncCallback<Boolean>() {
            @Override public void handleResponse(Boolean isValid) {
                if (!Boolean.TRUE.equals(isValid)) {
                    safeGo(SignInActivity.class);
                    return;
                }

                BackendlessUser current = Backendless.UserService.CurrentUser();
                if (current != null && current.getObjectId() != null) {
                    fetchUser(current.getObjectId());
                    return;
                }

                String uid = Backendless.UserService.loggedInUser();
                if (uid == null || uid.isEmpty()) {
                    uid = getSharedPreferences("TeacherPrefs", MODE_PRIVATE)
                            .getString("loggedInUserId", null);
                }
                if (uid == null || uid.isEmpty()) {
                    safeGo(SignInActivity.class);
                    return;
                }

                fetchUser(uid);
            }
            @Override public void handleFault(BackendlessFault fault) {
                safeGo(SignInActivity.class);
            }
        });
    }

    private void fetchUser(String uid) {
        Backendless.Data.of(BackendlessUser.class).findById(uid, new AsyncCallback<BackendlessUser>() {
            @Override public void handleResponse(BackendlessUser user) {
                if (user == null) { safeGo(SignInActivity.class); return; }

                boolean emailConfirmed = true;
                Object ec = user.getProperty("emailConfirmed");
                if (ec instanceof Boolean) emailConfirmed = (Boolean) ec;
                if (!emailConfirmed) {
                    Backendless.UserService.logout(new AsyncCallback<Void>() {
                        @Override public void handleResponse(Void response) {}
                        @Override public void handleFault(BackendlessFault fault) {}
                    });
                    safeGo(SignInActivity.class);
                    return;
                }

                String role = "";
                Object r = user.getProperty("role");
                if (r != null) role = String.valueOf(r);

                if ("TEACHER".equalsIgnoreCase(role))      safeGo(TeacherDashboardActivity.class);
                else if ("STUDENT".equalsIgnoreCase(role)) safeGo(StudentDashboardActivity.class);
                else                                       safeGo(SignInActivity.class);
            }
            @Override public void handleFault(BackendlessFault fault) {
                safeGo(SignInActivity.class);
            }
        });
    }

    private void safeGo(Class<?> target) {
        if (didNavigate.getAndSet(true)) return;

        main.removeCallbacks(timeoutFallback);

        long elapsed = System.currentTimeMillis() - startedAt;
        long wait = Math.max(0L, MIN_SPLASH_MS - elapsed);

        main.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            Intent i = new Intent(this, target);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        }, wait);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(timeoutFallback);
    }
}
