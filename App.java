package com.example.class_space_z;

import android.app.Application;
import android.util.Log;
import com.backendless.Backendless;
import com.example.class_space_z.models.ClassModel;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 1️⃣ Backendless initialize
        Backendless.setUrl("https://api.backendless.com");
        Backendless.initApp(
                this,
                "8ACC0B61-36C3-499C-98EF-F7E8E78FFD19",   // Application ID
                "3C5A55CA-E265-4AB3-B857-F0C0DD98F380"    // Android API Key
        );

        // 2️⃣ Table ↔ POJO mapping (IMPORTANT)
        Backendless.Data.mapTableToClass("Classes", ClassModel.class);

        // 3️⃣ Log confirmation
        Log.d("App", "Backendless initialized successfully");
    }
}
