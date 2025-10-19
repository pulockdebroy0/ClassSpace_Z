package com.example.class_space_z;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class TeacherProfileActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private ImageView imgProfile;
    private TextView tvName, tvEmail;
    private Button btnUpload, btnRemove, btnSave;
    private LinearLayout rowChangePassword;
    private SwitchCompat switchNotify;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private SharedPreferences prefs;

    private Uri pendingImageUri = null;
    private String photoUrl = "";

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    pendingImageUri = uri;
                    Glide.with(this).load(uri).circleCrop().into(imgProfile);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_profile);

        btnBack = findViewById(R.id.btnBack);
        imgProfile = findViewById(R.id.imgProfile);
        tvName = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);
        btnUpload = findViewById(R.id.btnUpload);
        btnRemove = findViewById(R.id.btnRemove);
        btnSave = findViewById(R.id.btnSave);
        rowChangePassword = findViewById(R.id.rowChangePassword);
        switchNotify = findViewById(R.id.switchNotify);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        prefs = getSharedPreferences("TeacherProfile", Context.MODE_PRIVATE);

        btnBack.setOnClickListener(v -> finish());
        btnUpload.setOnClickListener(v -> pickImage.launch("image/*"));
        btnRemove.setOnClickListener(v -> removePhoto());
        btnSave.setOnClickListener(v -> saveChanges());
        rowChangePassword.setOnClickListener(v -> sendPasswordReset());

        loadFromCache();
        fetchFromServer();
    }

    private void loadFromCache() {
        String name = prefs.getString("name", "");
        String email = prefs.getString("email", "");
        photoUrl = prefs.getString("photoUrl", "");
        boolean notify = prefs.getBoolean("notify", true);

        if (!name.isEmpty()) tvName.setText(name);
        if (!email.isEmpty()) tvEmail.setText(email);
        if (!photoUrl.isEmpty()) Glide.with(this).load(photoUrl).circleCrop().into(imgProfile);
        else imgProfile.setImageResource(R.drawable.ic_profile);
        switchNotify.setChecked(notify);
    }

    private void fetchFromServer() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        tvEmail.setText(user.getEmail() == null ? "" : user.getEmail());

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String chosenUrl = null;
                    String nameFromDb = null;
                    Boolean notify = null;
                    String userType = null;

                    if (doc.exists()) {
                        nameFromDb = doc.getString("name");
                        chosenUrl = doc.getString("photoUrl");
                        notify = doc.getBoolean("notify");
                        userType = doc.getString("userType");
                    }

                    if ((chosenUrl == null || chosenUrl.isEmpty()) && user.getPhotoUrl() != null) {
                        chosenUrl = user.getPhotoUrl().toString();
                        Map<String, Object> merge = new HashMap<>();
                        merge.put("photoUrl", chosenUrl);
                        db.collection("users").document(user.getUid())
                                .set(merge, com.google.firebase.firestore.SetOptions.merge());
                    }

                    if (nameFromDb == null || nameFromDb.isEmpty()) {
                        nameFromDb = user.getDisplayName() == null ? "Teacher" : user.getDisplayName();
                        Map<String, Object> merge = new HashMap<>();
                        merge.put("name", nameFromDb);
                        db.collection("users").document(user.getUid())
                                .set(merge, com.google.firebase.firestore.SetOptions.merge());
                    }

                    if (userType == null || !userType.equals("teacher")) {
                        Map<String, Object> merge = new HashMap<>();
                        merge.put("userType", "teacher");
                        db.collection("users").document(user.getUid())
                                .set(merge, com.google.firebase.firestore.SetOptions.merge());
                    }

                    tvName.setText(nameFromDb);
                    photoUrl = chosenUrl == null ? "" : chosenUrl;
                    if (!photoUrl.isEmpty())
                        Glide.with(this).load(photoUrl).circleCrop().into(imgProfile);
                    else
                        imgProfile.setImageResource(R.drawable.ic_profile);

                    switchNotify.setChecked(notify == null || notify);
                    cacheNow();
                })
                .addOnFailureListener(e -> {
                    if (auth.getCurrentUser() != null && auth.getCurrentUser().getPhotoUrl() != null) {
                        photoUrl = auth.getCurrentUser().getPhotoUrl().toString();
                        Glide.with(this).load(photoUrl).circleCrop().into(imgProfile);
                        cacheNow();
                    }
                });
    }

    private void saveChanges() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (pendingImageUri != null) {
            StorageReference ref = storage.getReference().child("profile_photos/" + user.getUid() + ".jpg");
            ref.putFile(pendingImageUri)
                    .continueWithTask(task -> ref.getDownloadUrl())
                    .addOnSuccessListener(uri -> {
                        photoUrl = uri.toString();
                        updateProfile(user.getUid(), photoUrl);
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show());
        } else {
            updateProfile(user.getUid(), photoUrl);
        }
    }

    private void updateProfile(String uid, String url) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", tvName.getText().toString().trim());
        map.put("email", tvEmail.getText().toString().trim());
        map.put("photoUrl", url == null ? "" : url);
        map.put("notify", switchNotify.isChecked());
        map.put("userType", "teacher");

        db.collection("users").document(uid)
                .set(map, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    cacheNow();
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show());
    }

    private void removePhoto() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (!photoUrl.isEmpty()) {
            StorageReference ref = storage.getReference().child("profile_photos/" + user.getUid() + ".jpg");
            ref.delete().addOnCompleteListener(task -> {});
        }
        photoUrl = "";
        pendingImageUri = null;
        imgProfile.setImageResource(R.drawable.ic_profile);

        Map<String, Object> map = new HashMap<>();
        map.put("photoUrl", "");
        db.collection("users").document(user.getUid()).set(map, com.google.firebase.firestore.SetOptions.merge());
        cacheNow();
    }

    private void sendPasswordReset() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "No email found", Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(user.getEmail())
                .addOnSuccessListener(unused -> Toast.makeText(this, "Reset email sent", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void cacheNow() {
        prefs.edit()
                .putString("name", tvName.getText().toString())
                .putString("email", tvEmail.getText().toString())
                .putString("photoUrl", photoUrl == null ? "" : photoUrl)
                .putBoolean("notify", switchNotify.isChecked())
                .apply();
    }
}