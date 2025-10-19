package com.example.class_space_z;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
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
import com.example.class_space_z.adapters.DiscussionAdapter;
import com.example.class_space_z.models.CommentModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscussionActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_ID   = "classId";
    public static final String EXTRA_CLASS_NAME = "className";

    private RecyclerView recyclerView;
    private EditText etNewComment;
    private Button btnAddComment;

    private DiscussionAdapter adapter;
    private SharedPreferences prefs;

    private String classId, userName, userType, className, userId;

    // Backendless RT
    private EventHandler<Map> discussionRT;

    private static final int PAGE_SIZE = 100;

    public static void launch(Context ctx, String classId, String className) {
        Intent it = new Intent(ctx, DiscussionActivity.class);
        it.putExtra(EXTRA_CLASS_ID, classId);
        it.putExtra(EXTRA_CLASS_NAME, className);
        ctx.startActivity(it);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discussion);

        // Backendless init (prefer Application.onCreate)
        Backendless.initApp(
                getApplicationContext(),
                "8ACC0B61-36C3-499C-98EF-F7E8E78FFD19",
                "3C5A55CA-E265-4AB3-B857-F0C0DD98F380"
        );

        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        // Inputs
        classId   = getIntent().getStringExtra(EXTRA_CLASS_ID);
        className = getIntent().getStringExtra(EXTRA_CLASS_NAME);

        SharedPreferences last = getSharedPreferences("LastClass", MODE_PRIVATE);
        if (TextUtils.isEmpty(classId)) {
            String fromPrefs = last.getString("lastClassId", "");
            if (!TextUtils.isEmpty(fromPrefs)) {
                classId = fromPrefs;
                if (TextUtils.isEmpty(className)) {
                    className = last.getString("lastClassName", "Class");
                }
                Log.w("Discuss","classId missing in Intent → using Prefs: " + classId);
            }
        }

        userName = prefs.getString("userName", "Unknown");
        userType = prefs.getString("userType", "student");
        userId   = prefs.getString("userId", "anon");

        // UI
        recyclerView  = findViewById(R.id.recyclerViewDiscussion);
        etNewComment  = findViewById(R.id.etNewComment);
        btnAddComment = findViewById(R.id.btnAddComment);

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recyclerView.setLayoutManager(lm);

        // Adapter + Callbacks
        adapter = new DiscussionAdapter(
                this,
                new ArrayList<>(),
                userType,
                new DiscussionAdapter.Callbacks() {
                    @Override public void onReply(CommentModel m, int pos)   { openThread(m); }
                    @Override public void onItemClick(CommentModel m, int p) { openThread(m); }
                    @Override public void onEdit(CommentModel m, int pos)    { editMessage(m); }
                    @Override public void onDelete(CommentModel m, int pos)  { deleteMessageWithPrompt(m); }
                    @Override public void onLike(CommentModel m, int pos)    { react(m, true); }
                    @Override public void onItemLongPress(CommentModel m, int pos) {
                        if (!canDelete(m)) {
                            Toast.makeText(DiscussionActivity.this,
                                    "You don't have permission to delete this message",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        deleteMessageWithPrompt(m);
                    }
                }
        );
        recyclerView.setAdapter(adapter);

        if (TextUtils.isEmpty(classId)) {
            etNewComment.setEnabled(false);
            btnAddComment.setEnabled(false);
            Toast.makeText(this, "No classId found. Please open Chat from a class.", Toast.LENGTH_LONG).show();
            Log.e("Discuss","Missing classId. Not attaching listeners.");
            return;
        }

        last.edit()
                .putString("lastClassId", classId)
                .putString("lastClassName", TextUtils.isEmpty(className) ? "Class" : className)
                .apply();

        // Initial load
        loadMessages();

        // Input handlers
        btnAddComment.setOnClickListener(v -> sendMessage());
        etNewComment.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });
    }

    @Override protected void onResume() {
        super.onResume();
        subscribeRealtime();
    }

    @Override protected void onPause() {
        super.onPause();
        unsubscribeRealtime();
    }

    // ------------------ Data ------------------

    private void loadMessages() {
        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause("classId='" + classId + "'");
        qb.setSortBy("timestamp ASC"); // or "createdAt ASC"
        qb.setPageSize(PAGE_SIZE);

        Backendless.Data.of("Discussion").find(qb, new AsyncCallback<List<Map>>() {
            @Override public void handleResponse(List<Map> res) {
                List<CommentModel> fresh = new ArrayList<>();
                if (res != null) {
                    for (Map m : res) fresh.add(mapToComment(m));
                }
                adapter.setItems(fresh);
                recyclerView.post(() ->
                        recyclerView.scrollToPosition(Math.max(0, fresh.size() - 1)));
            }
            @Override public void handleFault(BackendlessFault fault) {
                Log.e("Discuss", "loadMessages failed: " + fault.getMessage());
            }
        });
    }

    private CommentModel mapToComment(Map m) {
        CommentModel c = new CommentModel();
        c.setId((String) m.get("objectId"));
        c.setUserId(s(m.get("userId")));
        c.setUserName(s(m.get("userName")));
        c.setUserType(s(m.get("userType")));
        c.setCommentText(s(m.get("commentText")));
        Object ts = m.get("timestamp");
        c.setTimestamp(ts instanceof Number ? ((Number) ts).longValue() : 0L);
        Object likes = m.get("likes");
        Object dislikes = m.get("dislikes");
        Object replies = m.get("replies");
        c.setLikes(likes instanceof Number ? ((Number) likes).intValue() : 0);
        c.setDislikes(dislikes instanceof Number ? ((Number) dislikes).intValue() : 0);
        c.setReplies(replies instanceof Number ? ((Number) replies).intValue() : 0);
        return c;
    }

    // ------------------ Realtime ------------------

    private void subscribeRealtime() {
        if (TextUtils.isEmpty(classId)) return;
        if (discussionRT != null) unsubscribeRealtime();

        discussionRT = Backendless.Data.of("Discussion").rt();
        String where = "classId='" + classId + "'";

        // CREATE
        discussionRT.addCreateListener(where, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                CommentModel c = mapToComment(map);
                runOnUiThread(() -> {
                    List<CommentModel> cur = new ArrayList<>(adapter.getCurrentList());
                    cur.add(c);
                    adapter.setItems(cur);
                    recyclerView.scrollToPosition(Math.max(0, cur.size() - 1));
                });
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });

        // UPDATE
        discussionRT.addUpdateListener(where, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                CommentModel c = mapToComment(map);
                runOnUiThread(() -> {
                    List<CommentModel> cur = new ArrayList<>(adapter.getCurrentList());
                    int idx = findIndexById(cur, c.getId());
                    if (idx != -1) {
                        cur.set(idx, c);
                        adapter.setItems(cur);
                    }
                });
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });

        // DELETE
        discussionRT.addDeleteListener(where, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map map) {
                String id = (String) map.get("objectId");
                runOnUiThread(() -> {
                    List<CommentModel> cur = new ArrayList<>(adapter.getCurrentList());
                    int idx = findIndexById(cur, id);
                    if (idx != -1) {
                        cur.remove(idx);
                        adapter.setItems(cur);
                    }
                });
            }
            @Override public void handleFault(BackendlessFault fault) { }
        });
    }

    private void unsubscribeRealtime() {
        if (discussionRT != null) {
            try {
                discussionRT.removeCreateListeners();
                discussionRT.removeUpdateListeners();
                discussionRT.removeDeleteListeners();
            } catch (Exception ignore) {}
            discussionRT = null;
        }
    }

    private int findIndexById(List<CommentModel> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).getId())) return i;
        }
        return -1;
    }

    // ------------------ Actions ------------------

    private void sendMessage() {
        String text = etNewComment.getText().toString().trim();
        if (TextUtils.isEmpty(text)) { Toast.makeText(this, "Enter a message", Toast.LENGTH_SHORT).show(); return; }
        if (TextUtils.isEmpty(classId)) { Toast.makeText(this, "No classId; cannot send", Toast.LENGTH_SHORT).show(); return; }

        Map<String, Object> data = new HashMap<>();
        data.put("classId", classId);
        data.put("userId", userId);
        data.put("userName", userName);
        data.put("userType", userType);
        data.put("commentText", text);
        data.put("createdAt", new Date());
        data.put("timestamp", System.currentTimeMillis());
        data.put("likes", 0);
        data.put("dislikes", 0);
        data.put("replies", 0);

        Backendless.Data.of("Discussion").save(data, new AsyncCallback<Map>() {
            @Override public void handleResponse(Map saved) {
                etNewComment.setText("");
                // RT will push new item
            }
            @Override public void handleFault(BackendlessFault fault) {
                Toast.makeText(DiscussionActivity.this, "Failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void react(CommentModel m, boolean like) {
        if (m.getId() == null || TextUtils.isEmpty(classId)) return;

        Backendless.Data.of("Discussion").findById(m.getId(), new AsyncCallback<Map>() {
            @Override public void handleResponse(Map obj) {
                if (obj == null) return;
                String field = like ? "likes" : "dislikes";
                int cur = obj.get(field) instanceof Number ? ((Number) obj.get(field)).intValue() : 0;
                obj.put(field, cur + 1);
                Backendless.Data.of("Discussion").save(obj, new AsyncCallback<Map>() {
                    @Override public void handleResponse(Map map) { /* RT will update UI */ }
                    @Override public void handleFault(BackendlessFault fault) {
                        Toast.makeText(DiscussionActivity.this, "Failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void handleFault(BackendlessFault fault) {
                Toast.makeText(DiscussionActivity.this, "Failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void editMessage(CommentModel m) {
        if (m.getId() == null || TextUtils.isEmpty(classId)) return;

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(m.getCommentText());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit message")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
                    String newText = input.getText().toString().trim();
                    if (TextUtils.isEmpty(newText)) return;

                    Backendless.Data.of("Discussion").findById(m.getId(), new AsyncCallback<Map>() {
                        @Override public void handleResponse(Map obj) {
                            if (obj == null) return;
                            obj.put("commentText", newText);
                            obj.put("updatedAt", new Date());
                            Backendless.Data.of("Discussion").save(obj, new AsyncCallback<Map>() {
                                @Override public void handleResponse(Map map) { /* RT will update UI */ }
                                @Override public void handleFault(BackendlessFault fault) {
                                    Toast.makeText(DiscussionActivity.this, "Failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        @Override public void handleFault(BackendlessFault fault) {
                            Toast.makeText(DiscussionActivity.this, "Failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
    }

    // delete with prompt (used by long-press & delete button)
    private void deleteMessageWithPrompt(CommentModel m) {
        if (!canDelete(m)) {
            Toast.makeText(this, "You don't have permission to delete this message", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete message?")
                .setMessage("This will remove the message permanently.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, which) -> deleteMessage(m))
                .show();
    }

    private void deleteMessage(CommentModel m) {
        if (m.getId() == null || TextUtils.isEmpty(classId)) return;
        String where = "objectId='" + m.getId() + "'";
        Backendless.Data.of("Discussion").remove(where, new AsyncCallback<Integer>() {
            @Override public void handleResponse(Integer count) { /* RT will remove from UI */ }
            @Override public void handleFault(BackendlessFault fault) {
                Toast.makeText(DiscussionActivity.this, "Failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------ Thread navigation ------------------

    private void openThread(CommentModel m) {
        if (m == null || TextUtils.isEmpty(m.getId())) {
            Log.w("Discussion", "openThread: invalid comment (null/missing ID)");
            Toast.makeText(this, "Unable to open thread", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, DiscussionThreadActivity.class);
        intent.putExtra(DiscussionThreadActivity.EXTRA_CLASS_ID, classId);
        intent.putExtra(DiscussionThreadActivity.EXTRA_PARENT_ID, m.getId());
        intent.putExtra(DiscussionThreadActivity.EXTRA_PARENT_TXT, m.getCommentText());
        intent.putExtra(DiscussionThreadActivity.EXTRA_PARENT_NAME, m.getUserName());
        intent.putExtra(DiscussionThreadActivity.EXTRA_PARENT_TIME, m.getTimestamp());
        intent.putExtra(DiscussionThreadActivity.EXTRA_PARENT_TYPE, m.getUserType()); // optional

        try {
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception e) {
            Log.e("Discussion", "Failed to open thread", e);
            Toast.makeText(this, "Error opening thread", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------ Permissions ------------------
    private boolean canDelete(CommentModel m) {
        if (m == null) return false;
        // Rule: teacher সব ডিলিট করতে পারবে; student শুধু নিজেরটা
        if ("teacher".equalsIgnoreCase(userType)) return true;
        return TextUtils.equals(userId, m.getUserId());
    }

    private static String s(Object o) { return o == null ? "" : String.valueOf(o); }
}
