package com.example.class_space_z;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
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
import com.example.class_space_z.adapters.RepliesAdapter;
import com.example.class_space_z.models.CommentModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

public class DiscussionThreadActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_ID    = "classId";
    public static final String EXTRA_PARENT_ID   = "parentId";
    public static final String EXTRA_PARENT_TXT  = "parentText";
    public static final String EXTRA_PARENT_NAME = "parentName";
    public static final String EXTRA_PARENT_TIME = "parentTimeMillis";
    public static final String EXTRA_PARENT_TYPE = "parentType"; // NEW (optional)

    private TextView tvParentName, tvParentText, tvParentTime;
    private RecyclerView rvReplies;
    private EditText etReply;
    private Button btnSend;

    private final List<CommentModel> replies = new ArrayList<>();
    private RepliesAdapter adapter;

    private String classId, parentId, parentText, parentName, parentType;
    private long parentTimeMillis = 0L;

    // Backendless RT for Replies
    private EventHandler<Map> repliesRT;

    private static final int PAGE_SIZE = 200;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discussion_thread);

        Backendless.initApp(
                getApplicationContext(),
                "8ACC0B61-36C3-499C-98EF-F7E8E78FFD19",
                "3C5A55CA-E265-4AB3-B857-F0C0DD98F380"
        );

        Intent it = getIntent();
        classId          = it.getStringExtra(EXTRA_CLASS_ID);
        parentId         = it.getStringExtra(EXTRA_PARENT_ID);
        parentText       = it.getStringExtra(EXTRA_PARENT_TXT);
        parentName       = it.getStringExtra(EXTRA_PARENT_NAME);
        parentTimeMillis = it.getLongExtra(EXTRA_PARENT_TIME, 0L);
        parentType       = it.getStringExtra(EXTRA_PARENT_TYPE); // may be null

        if (TextUtils.isEmpty(classId) || TextUtils.isEmpty(parentId)) {
            Toast.makeText(this, "Missing thread info", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvParentName = findViewById(R.id.tvParentName);
        tvParentText = findViewById(R.id.tvParentText);
        tvParentTime = findViewById(R.id.tvParentTime);

        tvParentName.setText(TextUtils.isEmpty(parentName) ? "Unknown" : parentName);
        tvParentText.setText(parentText == null ? "" : parentText);
        if (parentTimeMillis > 0) {
            String t = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    .format(new Date(parentTimeMillis));
            tvParentTime.setText(t);
        } else {
            tvParentTime.setText("");
        }

        rvReplies = findViewById(R.id.recyclerReplies);
        etReply   = findViewById(R.id.etReply);
        btnSend   = findViewById(R.id.btnSendReply);

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvReplies.setLayoutManager(lm);

        adapter = new RepliesAdapter(this, replies);
        rvReplies.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Initial load
        loadReplies();

        // Send handlers
        btnSend.setOnClickListener(v -> sendReply());
        etReply.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendReply(); return true; }
            return false;
        });
    }

    @Override protected void onResume() {
        super.onResume();
        subscribeRealtimeReplies();
    }

    @Override protected void onPause() {
        super.onPause();
        unsubscribeRealtimeReplies();
    }

    private void loadReplies() {
        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause("classId = '" + classId + "' AND parentId = '" + parentId + "'");
        qb.setSortBy("timestamp ASC");
        qb.setPageSize(PAGE_SIZE);

        Backendless.Data.of("Replies").find(qb, new AsyncCallback<List<Map>>() {
            @Override public void handleResponse(List<Map> res) {
                List<CommentModel> fresh = new ArrayList<>();
                if (res != null) {
                    for (Map row : res) fresh.add(mapToComment(row));
                }
                replies.clear();
                replies.addAll(fresh);
                adapter.notifyDataSetChanged();
                rvReplies.scrollToPosition(Math.max(0, replies.size() - 1));
            }
            @Override public void handleFault(BackendlessFault fault) {
                Toast.makeText(DiscussionThreadActivity.this, "Load failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private CommentModel mapToComment(Map row) {
        CommentModel m = new CommentModel();
        m.setId((String) row.get("objectId"));
        m.setUserId(asString(row.get("userId")));
        m.setUserName(asString(row.get("userName")));
        m.setUserType(asString(row.get("userType")));
        m.setCommentText(asString(row.get("commentText")));
        Object ts = row.get("timestamp");
        m.setTimestamp(ts instanceof Number ? ((Number) ts).longValue() : 0L);
        m.setLikes(0);
        m.setDislikes(0);
        m.setReplies(0);
        return m;
    }

    private void subscribeRealtimeReplies() {
        if (TextUtils.isEmpty(classId) || TextUtils.isEmpty(parentId)) return;
        if (repliesRT != null) unsubscribeRealtimeReplies();

        repliesRT = Backendless.Data.of("Replies").rt();
        String where = "classId = '" + classId + "' AND parentId = '" + parentId + "'";

        repliesRT.addCreateListener(where, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                CommentModel m = mapToComment(map);
                runOnUiThread(() -> {
                    replies.add(m);
                    adapter.notifyItemInserted(replies.size() - 1);
                    rvReplies.scrollToPosition(Math.max(0, replies.size() - 1));
                });
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });

        repliesRT.addUpdateListener(where, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                CommentModel m = mapToComment(map);
                runOnUiThread(() -> {
                    int idx = findById(m.getId());
                    if (idx != -1) {
                        replies.set(idx, m);
                        adapter.notifyItemChanged(idx);
                    }
                });
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });

        repliesRT.addDeleteListener(where, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                String id = (String) map.get("objectId");
                runOnUiThread(() -> {
                    int idx = findById(id);
                    if (idx != -1) {
                        replies.remove(idx);
                        adapter.notifyItemRemoved(idx);
                    }
                });
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });
    }

    private void unsubscribeRealtimeReplies() {
        if (repliesRT != null) {
            try {
                repliesRT.removeCreateListeners();
                repliesRT.removeUpdateListeners();
                repliesRT.removeDeleteListeners();
            } catch (Exception ignore) {}
            repliesRT = null;
        }
    }

    private int findById(String id) {
        for (int i = 0; i < replies.size(); i++) {
            if (id.equals(replies.get(i).getId())) return i;
        }
        return -1;
    }

    private void sendReply() {
        String text = etReply.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "Write a reply", Toast.LENGTH_SHORT).show();
            return;
        }


        String uid  = "anon";
        String name = "User";

        Map<String, Object> data = new HashMap<>();
        data.put("parentId", parentId);
        data.put("classId", classId);
        data.put("userId", uid);
        data.put("userName", name);
        data.put("userType", "student");
        data.put("commentText", text);
        data.put("createdAt", new Date());
        data.put("timestamp", System.currentTimeMillis());

        Backendless.Data.of("Replies").save(data, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map savedReply) {
                etReply.setText("");
                // bump parent Discussion.replies++
                Backendless.Data.of("Discussion").findById(parentId, new AsyncCallback<Map>() {
                    @Override public void handleResponse(Map parent) {
                        if (parent == null) return;
                        int cur = parent.get("replies") instanceof Number ? ((Number) parent.get("replies")).intValue() : 0;
                        parent.put("replies", cur + 1);
                        Backendless.Data.of("Discussion").save(parent, new AsyncCallback<Map>() {
                            @Override public void handleResponse(Map updated) { }
                            @Override public void handleFault(BackendlessFault fault) {
                                Toast.makeText(DiscussionThreadActivity.this, "Failed to bump count: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    @Override public void handleFault(BackendlessFault fault) {
                        Toast.makeText(DiscussionThreadActivity.this, "Parent load failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void handleFault(BackendlessFault fault) {
                Toast.makeText(DiscussionThreadActivity.this, "Send failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String asString(Object o) { return o == null ? "" : String.valueOf(o); }
}
