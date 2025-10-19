package com.example.class_space_z;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.class_space_z.ui.SimpleChatFragment;
import com.example.class_space_z.ui.SimpleLiveFragment;
import com.example.class_space_z.ui.StudentAssignmentsFragment;
import com.example.class_space_z.ui.StudentMaterialsFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class StudentClassDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_ID   = "classId";
    public static final String EXTRA_CLASS_NAME = "className";
    public static final String EXTRA_LIVE_URL   = "liveUrl";

    private String classId, className, liveUrl;
    private MaterialToolbar topAppBar;
    private TabLayout tabs;
    private ViewPager2 pager;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_class_details);

        Intent it = getIntent();
        classId   = it != null ? it.getStringExtra(EXTRA_CLASS_ID)   : null;
        className = it != null ? it.getStringExtra(EXTRA_CLASS_NAME) : null;
        liveUrl   = it != null ? it.getStringExtra(EXTRA_LIVE_URL)   : null;
        if (className == null || className.trim().isEmpty()) className = "Class";

        topAppBar = findViewById(R.id.topAppBar);
        tabs      = findViewById(R.id.tabLayout);
        pager     = findViewById(R.id.viewPager);

        topAppBar.setTitle(className);
        topAppBar.setNavigationOnClickListener(v -> finish());
        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_join) {
                if (liveUrl != null && !liveUrl.trim().isEmpty()) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(liveUrl))); }
                    catch (Exception e) { Toast.makeText(this,"Can't open live link",Toast.LENGTH_SHORT).show(); }
                } else {
                    Toast.makeText(this, "Live link not available", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });

        final String[] titles = {"Live", "Assignments", "Materials", "Chat"};
        pager.setAdapter(new androidx.viewpager2.adapter.FragmentStateAdapter(this) {
            @Override public int getItemCount() { return titles.length; }
            @Override public Fragment createFragment(int position) {
                switch (position) {
                    case 0:  return SimpleLiveFragment.newInstance(classId, liveUrl, className);
                    case 1:  return StudentAssignmentsFragment.newInstance(classId);
                    case 2:  return StudentMaterialsFragment.newInstance(classId);
                    default: return SimpleChatFragment.newInstance(classId, className);
                }
            }
        });
        pager.setOffscreenPageLimit(1);
        new TabLayoutMediator(tabs, pager, (tab, pos) -> tab.setText(titles[pos])).attach();
    }
}
