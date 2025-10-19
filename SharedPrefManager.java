package com.example.class_space_z;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefManager {

    // আগেরটাই রাখছি যাতে পুরোনো ডেটা ব্রেক না হয়
    private static final String PREF_NAME   = "Assignments";

    // Login/Role keys
    private static final String KEY_LOGGED  = "is_logged_in";
    private static final String KEY_ROLE    = "user_role"; // "teacher" / "student"

    private final SharedPreferences prefs;

    public SharedPrefManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ---------- Assignment feedback (তোমার আগের মেথডগুলো) ----------
    public void saveFeedback(String assignmentId, String grade, String feedback) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(assignmentId + "_grade", grade);
        editor.putString(assignmentId + "_feedback", feedback);
        editor.apply();
    }

    public String getGrade(String assignmentId) {
        return prefs.getString(assignmentId + "_grade", "");
    }

    public String getFeedback(String assignmentId) {
        return prefs.getString(assignmentId + "_feedback", "");
    }

    // ---------- Login + Role (Splash/SignIn এর জন্য নতুন) ----------
    /** লগইন স্টেট + রোল একসাথে সেভ */
    public void setLogin(boolean loggedIn, String role) {
        prefs.edit()
                .putBoolean(KEY_LOGGED, loggedIn)
                .putString(KEY_ROLE, role)
                .apply();
    }

    /** শুধু লগইন স্টেট আপডেট দরকার হলে */
    public void setLoggedIn(boolean loggedIn) {
        prefs.edit().putBoolean(KEY_LOGGED, loggedIn).apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED, false);
    }

    /** রোল রিড; না পেলে ডিফল্ট "student" */
    public String getUserRole() {
        return prefs.getString(KEY_ROLE, "student");
    }

    /** শুধু রোল আলাদা করে আপডেট করতে চাইলে */
    public void setUserRole(String role) {
        prefs.edit().putString(KEY_ROLE, role).apply();
    }

    /** সম্পূর্ণ লগআউট করলে লোকাল ডেটা ক্লিয়ার */
    public void logout() {
        prefs.edit().clear().apply();
    }
}
