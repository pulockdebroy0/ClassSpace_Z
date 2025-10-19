package com.example.class_space_z;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.ViewGroup;

public class StaticTextFragment extends Fragment {
    private static final String ARG_T = "t";
    public static StaticTextFragment newInstance(String text) {
        StaticTextFragment f = new StaticTextFragment();
        Bundle b = new Bundle(); b.putString(ARG_T, text); f.setArguments(b); return f;
    }
    @Nullable @Override
    public View onCreateView(LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        TextView tv = new TextView(requireContext());
        tv.setText(getArguments() != null ? getArguments().getString(ARG_T, "") : "");
        tv.setTextSize(16f);
        tv.setTextColor(0xFF111827);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }
}
