package com.example.class_space_z;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import com.example.class_space_z.ui.PreClassLobbyActivity;

import com.example.class_space_z.utils.AssignmentStore;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class TeacherClassDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_CLASS_ID   = "classId";
    public static final String EXTRA_CLASS_NAME = "className";

    private TabLayout tabs;
    private ViewPager2 pager;

    private long lastLiveTapAt = 0L;
    private long lastMaterialsTapAt = 0L;
    private long lastAssignmentsTapAt = 0L;
    private long lastStudentsTapAt = 0L;
    private long lastChatTapAt = 0L;

    private String classId;
    private String className;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_class_details);

        Intent i = getIntent();
        classId   = safeGet(i, EXTRA_CLASS_ID, "class_id", "CLASS_ID", "cid");
        className = safeGet(i, EXTRA_CLASS_NAME, "class_name", "CLASS_NAME", "cname");
        if (classId == null) classId = "";
        if (className == null || className.trim().isEmpty()) className = "Class";

        TextView title = findViewById(R.id.tvTitle);
        if (title != null) title.setText(className);
        if (findViewById(R.id.btnClose) != null) {
            findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        }

        final String[] titles = {"Live Class", "Assignments", "Students", "Materials", "Chat"};
        pager = findViewById(R.id.viewPager);
        tabs  = findViewById(R.id.tabLayout);

        if (pager != null) {
            pager.setAdapter(new StaticPagerAdapter(this, titles));
            pager.setOffscreenPageLimit(1);
            pager.setCurrentItem(0, false);
            pager.setPageTransformer((page, position) -> {
                page.setTranslationX(-position * page.getWidth() * 0.12f);
                page.setAlpha(1f - Math.min(1f, Math.abs(position) * 0.30f));
                float scale = 0.98f + (1f - Math.min(1f, Math.abs(position))) * 0.02f;
                page.setScaleY(scale);
            });
        }

        if (tabs != null && pager != null) {
            new TabLayoutMediator(tabs, pager, (tab, pos) -> {
                tab.setText(titles[pos]);
                tab.setContentDescription(titles[pos]);
            }).attach();

            tabs.post(() -> {
                wireTab(safeTab(0), () -> { if (debounce(0)) launchLiveLobby(); });
                wireTab(safeTab(1), () -> { if (debounce(1)) launchAssignments(); });
                wireTab(safeTab(2), () -> { if (debounce(2)) launchStudents(); });
                wireTab(safeTab(3), () -> { if (debounce(3)) launchMaterials(); });
                wireTab(safeTab(4), () -> { if (debounce(4)) launchDiscussion(); });
                updateAssignmentsBadge();
            });

            tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab tab)   { animateTab(tab, true); }
                @Override public void onTabUnselected(TabLayout.Tab tab) { animateTab(tab, false); }
                @Override public void onTabReselected(TabLayout.Tab tab) { }
                private void animateTab(TabLayout.Tab tab, boolean sel) {
                    if (tab == null || tab.view == null) return;
                    float from = sel ? 1f : 1.06f, to = sel ? 1.06f : 1f;
                    ValueAnimator a = ValueAnimator.ofFloat(from, to);
                    a.setDuration(160);
                    a.addUpdateListener(v -> {
                        float s = (float) v.getAnimatedValue();
                        tab.view.setScaleX(s);
                        tab.view.setScaleY(s);
                    });
                    a.start();
                }
            });
        }

        if (findViewById(R.id.navDashboard) != null)
            findViewById(R.id.navDashboard).setOnClickListener(v ->
                    startActivity(new Intent(this, TeacherDashboardActivity.class)));
        if (findViewById(R.id.navClasses) != null)
            findViewById(R.id.navClasses).setOnClickListener(v ->
                    startActivity(new Intent(this, TeacherClassesActivity.class)));
    }

    private TabLayout.Tab safeTab(int index) {
        return (tabs != null && index >= 0 && index < tabs.getTabCount()) ? tabs.getTabAt(index) : null;
    }

    private String safeGet(Intent it, String... keys) {
        if (it == null) return null;
        for (String k : keys) {
            String v = it.getStringExtra(k);
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return null;
    }

    private boolean debounce(int which) {
        long now = System.currentTimeMillis();
        long last = 0L;
        switch (which) {
            case 0: last = lastLiveTapAt; break;
            case 1: last = lastAssignmentsTapAt; break;
            case 2: last = lastStudentsTapAt; break;
            case 3: last = lastMaterialsTapAt; break;
            default: last = lastChatTapAt; break;
        }
        if (now - last < 500) return false;
        switch (which) {
            case 0: lastLiveTapAt = now; break;
            case 1: lastAssignmentsTapAt = now; break;
            case 2: lastStudentsTapAt = now; break;
            case 3: lastMaterialsTapAt = now; break;
            default: lastChatTapAt = now; break;
        }
        return true;
    }

    private void wireTab(TabLayout.Tab tab, Runnable action) {
        if (tab != null && tab.view != null && action != null) {
            tab.view.setOnClickListener(v -> action.run());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAssignmentsBadge();
    }

    private void updateAssignmentsBadge() {
        if (tabs == null) return;
        TabLayout.Tab t = tabs.getTabAt(1);
        if (t == null) return;

        if (classId == null || classId.trim().isEmpty()) {
            if (t.getBadge() != null) t.removeBadge();
            return;
        }

        int count = 0;
        try {
            count = AssignmentStore.getCountForClass(getApplicationContext(), classId);
        } catch (Exception ignore) {}

        if (count > 0) {
            BadgeDrawable b = t.getOrCreateBadge();
            b.setVisible(true);
            b.setNumber(count);
        } else if (t.getBadge() != null) {
            t.removeBadge();
        }
    }

    private void launchLiveLobby() {
        if (classId == null || classId.isEmpty()) return;
        Intent it = new Intent(this, PreClassLobbyActivity.class);
        it.putExtra(EXTRA_CLASS_ID,   classId);
        it.putExtra(EXTRA_CLASS_NAME, className);
        startActivity(it);
    }
    private void launchMaterials() {
        if (classId == null || classId.isEmpty()) return;
        Intent it = new Intent(this, TeacherMaterialsActivity.class);
        it.putExtra(EXTRA_CLASS_ID,   classId);
        it.putExtra(EXTRA_CLASS_NAME, className);
        startActivity(it);
    }
    private void launchAssignments() {
        if (classId == null || classId.isEmpty()) return;
        Intent it = new Intent(this, TeacherAssignmentsActivity.class);
        it.putExtra(EXTRA_CLASS_ID,   classId);
        it.putExtra(EXTRA_CLASS_NAME, className);
        startActivity(it);
    }
    private void launchStudents() {
        if (classId == null || classId.isEmpty()) return;
        Intent it = new Intent(this, ClassStudentsActivity.class);
        it.putExtra(EXTRA_CLASS_ID,   classId);
        it.putExtra(EXTRA_CLASS_NAME, className);
        startActivity(it);
    }
    private void launchDiscussion() {
        if (classId == null || classId.isEmpty()) return;

        getSharedPreferences("LastClass", MODE_PRIVATE)
                .edit()
                .putString("lastClassId", classId)
                .putString("lastClassName", className)
                .apply();

        Log.d("Details","Open Chat → classId="+classId+", name="+className);
        DiscussionActivity.launch(this, classId, className);
    }

    static class StaticPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
        private final String[] titles;
        StaticPagerAdapter(AppCompatActivity a, String[] titles) { super(a); this.titles = titles; }
        @Override public int getItemCount() { return titles.length; }
        @Override public Fragment createFragment(int position) {
            return StaticTextFragment.newInstance(titles[position]);
        }
    }
}
