package com.example.class_space_z;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.class_space_z.adapters.NotificationsAdapter;
import com.example.class_space_z.models.NotificationItem;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TeacherNotificationsActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private RecyclerView recycler;
    private Button btnMarkAll;
    private ImageButton btnBack;
    private NotificationsAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final String USER_TYPE = "teacher";
    private String currentCat = "assignments";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_notifications);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences("NotificationsCache", Context.MODE_PRIVATE);

        tabLayout = findViewById(R.id.tabLayout);
        recycler = findViewById(R.id.recyclerNotifications);
        btnMarkAll = findViewById(R.id.btnMarkAll);
        btnBack = findViewById(R.id.btnBack);

        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.assignments_tab, 0)), true);
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.classes_tab, 0)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.general_tab, 0)));

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationsAdapter(this, (item, position) -> markSingleAsRead(item));
        recycler.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnMarkAll.setOnClickListener(v -> markAllAsRead());

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                int p = tab.getPosition();
                currentCat = p == 0 ? "assignments" : (p == 1 ? "classes" : "general");
                loadNotifications(currentCat, true);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        loadNotifications(currentCat, false);
        loadNotifications(currentCat, true);
        updateTabCounts();
    }

    private void loadNotifications(@NonNull String category, boolean fromNetwork) {
        if (!fromNetwork) {
            List<NotificationItem> cached = getCached(category);
            if (!cached.isEmpty()) adapter.submitList(cached);
        }

        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "demo";
        db.collection("notifications")
                .whereEqualTo("category", category)
                .whereIn("target", java.util.Arrays.asList("all", USER_TYPE))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    Set<String> readSet = new HashSet<>();
                    db.collection("users").document(uid)
                            .collection("reads").get()
                            .addOnSuccessListener(reads -> {
                                for (DocumentSnapshot r : reads.getDocuments()) readSet.add(r.getId());
                                List<NotificationItem> list = new ArrayList<>();
                                for (DocumentSnapshot d : snap.getDocuments()) {
                                    NotificationItem n = d.toObject(NotificationItem.class);
                                    if (n == null) n = new NotificationItem();
                                    n.setId(d.getId());
                                    if (n.getTitle() == null) n.setTitle("(No title)");
                                    if (n.getSubtitle() == null) n.setSubtitle("");
                                    if (n.getCategory() == null) n.setCategory(category);
                                    if (n.getTarget() == null) n.setTarget("all");
                                    if (n.getCreatedAt() == null) n.setCreatedAt(Timestamp.now());
                                    n.setRead(readSet.contains(n.getId()));
                                    list.add(n);
                                }
                                adapter.submitList(list);
                                cache(category, list);
                                updateTabCounts();
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateTabCounts() {
        updateOneCount("assignments", 0);
        updateOneCount("classes", 1);
        updateOneCount("general", 2);
    }

    private void updateOneCount(String cat, int tabIndex) {
        db.collection("notifications")
                .whereEqualTo("category", cat)
                .whereIn("target", java.util.Arrays.asList("all", USER_TYPE))
                .get()
                .addOnSuccessListener(snap -> {
                    int count = snap.size();
                    TabLayout.Tab t = tabLayout.getTabAt(tabIndex);
                    if (t != null) {
                        if (tabIndex == 0) t.setText(getString(R.string.assignments_tab, count));
                        else if (tabIndex == 1) t.setText(getString(R.string.classes_tab, count));
                        else t.setText(getString(R.string.general_tab, count));
                    }
                });
    }

    private void markSingleAsRead(@NonNull NotificationItem item) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "demo";
        db.collection("users").document(uid)
                .collection("reads").document(item.getId())
                .set(new java.util.HashMap<String, Object>() {{
                    put("read", true);
                    put("readAt", com.google.firebase.Timestamp.now());
                }})
                .addOnSuccessListener(unused -> {
                    item.setRead(true);
                    adapter.notifyDataSetChanged();
                });
    }

    private void markAllAsRead() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "demo";
        List<NotificationItem> list = adapter.getCurrent();
        com.google.firebase.firestore.WriteBatch batch = db.batch();
        for (NotificationItem n : list) {
            batch.set(
                    db.collection("users").document(uid)
                            .collection("reads").document(n.getId()),
                    new java.util.HashMap<String, Object>() {{
                        put("read", true);
                        put("readAt", com.google.firebase.Timestamp.now());
                    }}
            );
            n.setRead(true);
        }
        batch.commit().addOnSuccessListener(unused -> {
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "All marked as read", Toast.LENGTH_SHORT).show();
        });
    }

    private void cache(String category, List<NotificationItem> list) {
        prefs.edit().putString(cacheKey(category), gson.toJson(list)).apply();
    }

    private List<NotificationItem> getCached(String category) {
        String json = prefs.getString(cacheKey(category), "");
        if (json.isEmpty()) return new ArrayList<>();
        Type type = new TypeToken<List<NotificationItem>>() {}.getType();
        try { return gson.fromJson(json, type); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private String cacheKey(String category) {
        return "cache_" + USER_TYPE + "_" + category;
    }
}