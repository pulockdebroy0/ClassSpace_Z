package com.example.class_space_z;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.backendless.Backendless;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;          // ✅ correct import
import com.backendless.files.BackendlessFile;               // ✅ upload result type
import com.backendless.persistence.DataQueryBuilder;
import com.backendless.rt.data.EventHandler;
import com.example.class_space_z.adapters.MaterialsAdapter;
import com.example.class_space_z.models.MaterialItem;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeacherMaterialsActivity extends AppCompatActivity {

    private Uri selectedFileUri = null;
    private String classId = "CLASS_ID";

    private RecyclerView recyclerView;
    private MaterialsAdapter adapter;
    private final ArrayList<MaterialItem> data = new ArrayList<>();

    private ActivityResultLauncher<String[]> pickFileLauncher;

    private static final String TABLE_MATERIALS = "Materials";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_materials);

        // Backendless.initApp(this, "YOUR-APP-ID", "YOUR-ANDROID-API-KEY");

        pickFileLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (Exception ignored) {}
                        selectedFileUri = uri;
                        Toast.makeText(this, "File selected: " + getFileNameFromUri(uri), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        String fromIntent = getIntent().getStringExtra(TeacherClassDetailsActivity.EXTRA_CLASS_ID);
        if (fromIntent != null && !fromIntent.trim().isEmpty()) classId = fromIntent;

        Button btnUpload = findViewById(R.id.btn_upload);
        btnUpload.setOnClickListener(v -> showUploadDialog());

        recyclerView = findViewById(R.id.recyclerMaterials);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MaterialsAdapter(data, classId);
        recyclerView.setAdapter(adapter);

        loadMaterials();
        attachRealtimeListeners();
    }

    private void showUploadDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_upload_material, null);
        dialog.setContentView(view);

        EditText etTitle       = view.findViewById(R.id.etTitle);
        RadioGroup radioGroup  = view.findViewById(R.id.radioGroupType);
        Button btnChooseFile   = view.findViewById(R.id.btnChooseFile);
        Button btnUploadNow    = view.findViewById(R.id.btnUploadNow);

        btnChooseFile.setOnClickListener(v ->
                pickFileLauncher.launch(new String[]{"application/pdf", "video/*"})
        );

        btnUploadNow.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();

            int checkedId = radioGroup.getCheckedRadioButtonId();
            String type;
            if (checkedId == R.id.rbPdf) type = "PDF";
            else if (checkedId == R.id.rbVideo) type = "Video";
            else {
                Toast.makeText(this, "Select type (PDF/Video)", Toast.LENGTH_SHORT).show();
                return;
            }

            if (title.isEmpty() || selectedFileUri == null) {
                Toast.makeText(this, "Enter title & choose file first", Toast.LENGTH_SHORT).show();
                return;
            }

            saveFileLocallyAndMeta(selectedFileUri, title, type);
            selectedFileUri = null;
            dialog.dismiss();
        });

        dialog.show();
    }

    /** Save file to app storage + upload to Backendless Files + save metadata to Backendless Data */
    private void saveFileLocallyAndMeta(Uri fileUri, String title, String type) {
        try {
            String fileId = UUID.randomUUID().toString();

            // MIME + extension
            String mime = getContentResolver().getType(fileUri);
            if (mime == null) mime = type.equalsIgnoreCase("PDF") ? "application/pdf" : "video/*";

            String ext = ".bin";
            if ("application/pdf".equals(mime)) ext = ".pdf";
            else if (mime.startsWith("video/")) ext = ".mp4";

            File baseDir = getExternalFilesDir(null);
            if (baseDir == null) {
                Toast.makeText(this, "Storage dir not available", Toast.LENGTH_SHORT).show();
                return;
            }
            File dir = new File(baseDir, "materials");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(this, "Failed to create folder", Toast.LENGTH_SHORT).show();
                return;
            }

            File localFile = new File(dir, fileId + ext);

            long written = 0;
            try (InputStream in = getContentResolver().openInputStream(fileUri);
                 OutputStream out = new FileOutputStream(localFile)) {
                if (in == null) throw new IllegalStateException("InputStream is null");
                byte[] buffer = new byte[16 * 1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    written += len;
                }
                out.flush();
            }

            if (written == 0 || !localFile.exists()) {
                Toast.makeText(this, "File copy failed!", Toast.LENGTH_SHORT).show();
                return;
            }

            // -------- capture finals for inner callbacks --------
            final String mimeFinal = mime;
            final long sizeBytesFinal = written;
            final String fileIdFinal = fileId;
            final String titleFinal = title;
            final String typeFinal = type;
            final File localFileFinal = localFile;

            // Upload to Backendless Files: /materials/<classId>/
            String remoteFolder = "/materials/" + classId;
            Backendless.Files.upload(localFileFinal, remoteFolder, new AsyncCallback<BackendlessFile>() {
                @Override
                public void handleResponse(BackendlessFile bf) {
                    // Build metadata map for Backendless Data
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", fileIdFinal);
                    item.put("classId", classId);
                    item.put("title", titleFinal);
                    item.put("type", typeFinal);
                    item.put("mime", mimeFinal);
                    item.put("localPath", localFileFinal.getAbsolutePath());
                    item.put("remoteUrl", bf != null ? bf.getFileURL() : null);
                    item.put("timestamp", System.currentTimeMillis());
                    item.put("sizeBytes", sizeBytesFinal);

                    final long size = localFileFinal.length();

                    Backendless.Data.of(TABLE_MATERIALS).save(item, new AsyncCallback<Map>() {
                        @Override
                        public void handleResponse(Map saved) {
                            Toast.makeText(TeacherMaterialsActivity.this,
                                    "Saved " + (size / 1024) + " KB", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void handleFault(BackendlessFault fault) {
                            Toast.makeText(TeacherMaterialsActivity.this,
                                    "Data save error: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void handleFault(BackendlessFault fault) {
                    Toast.makeText(TeacherMaterialsActivity.this,
                            "File upload failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "Save Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Initial load from Backendless Data */
    private void loadMaterials() {
        DataQueryBuilder qb = DataQueryBuilder.create();
        qb.setWhereClause("classId = '" + classId + "'");
        qb.setSortBy(Arrays.asList("timestamp DESC"));
        qb.setPageSize(50);

        Backendless.Data.of(TABLE_MATERIALS).find(qb, new AsyncCallback<List<Map>>() {
            @Override
            public void handleResponse(List<Map> result) {
                data.clear();
                for (Map map : result) {
                    MaterialItem item = mapToMaterialItem(map);
                    data.add(item);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Toast.makeText(TeacherMaterialsActivity.this,
                        "Load failed: " + fault.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Realtime listeners with Backendless RT Data */
    private void attachRealtimeListeners() {
        EventHandler<Map> rt = Backendless.Data.of(TABLE_MATERIALS).rt();

        String where = "classId = '" + classId + "'";

        rt.addCreateListener(where, new AsyncCallback<Map>() {
            @Override
            public void handleResponse(Map created) {
                MaterialItem item = mapToMaterialItem(created);
                data.add(0, item);
                runOnUiThread(() -> adapter.notifyItemInserted(0));
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                runOnUiThread(() ->
                        Toast.makeText(TeacherMaterialsActivity.this,
                                "RT create error: " + fault.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        rt.addUpdateListener(where, new AsyncCallback<Map>() {
            @Override
            public void handleResponse(Map updated) {
                MaterialItem item = mapToMaterialItem(updated);
                int idx = findIndexById(item.getId());
                if (idx >= 0) {
                    data.set(idx, item);
                    runOnUiThread(() -> adapter.notifyItemChanged(idx));
                } else {
                    data.add(0, item);
                    runOnUiThread(() -> adapter.notifyItemInserted(0));
                }
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                runOnUiThread(() ->
                        Toast.makeText(TeacherMaterialsActivity.this,
                                "RT update error: " + fault.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        rt.addDeleteListener(where, new AsyncCallback<Map>() {
            @Override
            public void handleResponse(Map deleted) {
                String id = (String) deleted.get("id");
                int idx = findIndexById(id);
                if (idx >= 0) {
                    data.remove(idx);
                    runOnUiThread(() -> adapter.notifyItemRemoved(idx));
                }
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                runOnUiThread(() ->
                        Toast.makeText(TeacherMaterialsActivity.this,
                                "RT delete error: " + fault.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private int findIndexById(String id) {
        for (int i = 0; i < data.size(); i++) {
            if (id != null && id.equals(data.get(i).getId())) return i;
        }
        return -1;
    }

    private MaterialItem mapToMaterialItem(Map map) {
        MaterialItem item = new MaterialItem();
        item.setId(asString(map.get("id")));
        item.setTitle(asString(map.get("title")));
        item.setType(asString(map.get("type")));
        item.setMime(asString(map.get("mime")));
        item.setLocalPath(asString(map.get("localPath")));
        item.setRemoteUrl(asString(map.get("remoteUrl")));
        Object ts = map.get("timestamp");
        long t = 0L;
        if (ts instanceof Number) t = ((Number) ts).longValue();
        else if (ts instanceof String) {
            try { t = Long.parseLong((String) ts); } catch (Exception ignored) {}
        }
        item.setTimestamp(t);
        Object sz = map.get("sizeBytes");
        long s = 0L;
        if (sz instanceof Number) s = ((Number) sz).longValue();
        else if (sz instanceof String) {
            try { s = Long.parseLong((String) sz); } catch (Exception ignored) {}
        }
        item.setSizeBytes(s);
        item.setClassId(asString(map.get("classId")));
        return item;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) result = cursor.getString(nameIndex);
            }
        } catch (Exception ignored) {}
        if (result == null) result = uri.getLastPathSegment();
        return result;
    }
}
