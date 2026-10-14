/*
**	Command & Conquer Generals Zero Hour(tm)
**	Copyright 2025 Electronic Arts Inc.
**
**	This program is free software: you can redistribute it and/or modify
**	it under the terms of the GNU General Public License as published by
**	the Free Software Foundation, either version 3 of the License, or
**	(at your option) any later version.
**
**	This program is distributed in the hope that it will be useful,
**	but WITHOUT ANY WARRANTY; without even the implied warranty of
**	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**	GNU General Public License for more details.
**
**	You should have received a copy of the GNU General Public License
**	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.generalsx.zerohour;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

// GeneralsX @feature Android cheat menu: a Cheat-Engine-style pop-up over the game.
//
// A small draggable button floats over the game; tapping it opens/closes the panel with the
// switches. Everything the switches do is decided in the engine (Common/GXCheats.cpp): they apply
// only to the human player on this device, never to AI players (enemy or ally), and not at all in
// LAN/online matches or replays. While no such game is running the panel says so and the engine
// ignores the switches. All methods run on the UI thread.
final class CheatMenu {

    // Keep in step with AndroidCheatBridge.cpp.
    private static final int SET_MAX_MONEY = 0;
    private static final int SET_FREEZE_MONEY = 1;
    private static final int SET_FAST_BUILD = 2;
    private static final int SET_ENERGY_FULL = 3;
    private static final int SET_ENERGY_BONUS = 4;
    private static final int QUERY_AVAILABLE = 0;
    private static final int QUERY_FLAGS = 1;
    private static final int QUERY_ENERGY_BONUS = 2;

    private static final int ENERGY_STEP = 50;      // kW per SeekBar step
    private static final int ENERGY_MAX_STEPS = 40; // 40 * 50 = 2000 kW

    private final Activity activity;
    private final ViewGroup parent;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView toggleButton;
    private LinearLayout panel;
    private ScrollView panelRoot;
    private TextView statusText;
    private Switch maxMoney;
    private Switch freezeMoney;
    private Switch fastBuild;
    private Switch energyFull;
    private SeekBar energySeek;
    private TextView energyLabel;
    private boolean syncing;
    private boolean nativeMissing;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    CheatMenu(Activity activity, ViewGroup parent) {
        this.activity = activity;
        this.parent = parent;
        build();
        handler.postDelayed(poll, 1000);
    }

    boolean isPanelShown() {
        return panelRoot != null && panelRoot.getVisibility() == View.VISIBLE;
    }

    void closePanel() {
        if (panelRoot != null) {
            panelRoot.setVisibility(View.GONE);
        }
    }

    void destroy() {
        handler.removeCallbacks(poll);
    }

    // ---- native bridge; an engine without these symbols just hides the menu ----

    private void set(int id, int value) {
        try {
            GeneralsZHActivity.nativeCheatSet(id, value);
        } catch (UnsatisfiedLinkError e) {
            nativeMissing = true;
        }
    }

    private int query(int id) {
        try {
            return GeneralsZHActivity.nativeCheatQuery(id);
        } catch (UnsatisfiedLinkError e) {
            nativeMissing = true;
            return 0;
        }
    }

    // ---- UI ----

    private void build() {
        toggleButton = new TextView(activity);
        toggleButton.setText("CHEAT");
        toggleButton.setTextColor(Color.WHITE);
        toggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        toggleButton.setGravity(Gravity.CENTER);
        toggleButton.setAlpha(0.75f);
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0xCC1E6B3A);
        chip.setStroke(dp(1), 0xFF7FD99A);
        chip.setCornerRadius(dp(8));
        toggleButton.setBackground(chip);
        RelativeLayout.LayoutParams buttonParams = new RelativeLayout.LayoutParams(dp(56), dp(32));
        buttonParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        buttonParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        buttonParams.leftMargin = dp(8);
        buttonParams.topMargin = dp(90);
        installDragAndTap(toggleButton);
        parent.addView(toggleButton, buttonParams);

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable card = new GradientDrawable();
        card.setColor(0xE61B1F24);
        card.setStroke(dp(1), 0xFF5C6670);
        card.setCornerRadius(dp(10));
        panel.setBackground(card);
        panel.setClickable(true); // swallow touches so they do not reach the game underneath
        int pad = dp(12);
        panel.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(activity);
        title.setText(R.string.cheat_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(title);

        statusText = new TextView(activity);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusText.setPadding(0, dp(2), 0, dp(6));
        panel.addView(statusText);

        maxMoney = addSwitch(R.string.cheat_max_money, SET_MAX_MONEY);
        freezeMoney = addSwitch(R.string.cheat_freeze_money, SET_FREEZE_MONEY);
        fastBuild = addSwitch(R.string.cheat_fast_build, SET_FAST_BUILD);
        energyFull = addSwitch(R.string.cheat_energy_full, SET_ENERGY_FULL);

        energyLabel = new TextView(activity);
        energyLabel.setTextColor(Color.WHITE);
        energyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        energyLabel.setPadding(0, dp(6), 0, 0);
        panel.addView(energyLabel);

        energySeek = new SeekBar(activity);
        energySeek.setMax(ENERGY_MAX_STEPS);
        energySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateEnergyLabel(progress * ENERGY_STEP);
                if (fromUser && !syncing) {
                    set(SET_ENERGY_BONUS, progress * ENERGY_STEP);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        panel.addView(energySeek);

        TextView note = new TextView(activity);
        note.setText(R.string.cheat_note);
        note.setTextColor(0xFF9AA0A6);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        note.setPadding(0, dp(8), 0, 0);
        panel.addView(note);

        panelRoot = new ScrollView(activity);
        panelRoot.addView(panel);
        RelativeLayout.LayoutParams panelParams = new RelativeLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        panelParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        panelParams.leftMargin = dp(70);
        panelParams.topMargin = dp(20);
        panelParams.bottomMargin = dp(20);
        panelRoot.setVisibility(View.GONE);
        parent.addView(panelRoot, panelParams);

        updateEnergyLabel(0);
        refresh();
    }

    private Switch addSwitch(int labelRes, final int id) {
        Switch sw = new Switch(activity);
        sw.setText(labelRes);
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sw.setPadding(0, dp(4), 0, dp(4));
        sw.setOnCheckedChangeListener((CompoundButton b, boolean on) -> {
            if (!syncing) {
                set(id, on ? 1 : 0);
            }
        });
        panel.addView(sw);
        return sw;
    }

    // A short tap opens/closes the panel; dragging moves the button out of the way.
    private void installDragAndTap(final View button) {
        button.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY, startLeft, startTop;
            private boolean dragged;
            @Override public boolean onTouch(View v, MotionEvent e) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) v.getLayoutParams();
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY();
                        startLeft = lp.leftMargin; startTop = lp.topMargin;
                        dragged = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) dragged = true;
                        if (dragged) {
                            lp.leftMargin = Math.max(0, (int) (startLeft + dx));
                            lp.topMargin = Math.max(0, (int) (startTop + dy));
                            v.setLayoutParams(lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragged) {
                            togglePanel();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void togglePanel() {
        if (isPanelShown()) {
            closePanel();
        } else {
            panelRoot.setVisibility(View.VISIBLE);
            refresh();
        }
    }

    /** Reads the engine's current state into the controls (also tells if a game is running). */
    private void refresh() {
        if (nativeMissing) {
            toggleButton.setVisibility(View.GONE);
            closePanel();
            return;
        }
        boolean available = query(QUERY_AVAILABLE) != 0;
        if (nativeMissing) {
            return;
        }
        toggleButton.setAlpha(available ? 0.9f : 0.4f);
        if (!isPanelShown()) {
            return;
        }
        statusText.setText(available ? R.string.cheat_status_ready : R.string.cheat_status_unavailable);
        statusText.setTextColor(available ? 0xFF7FD99A : 0xFFFFB74D);
        int flags = query(QUERY_FLAGS);
        int bonus = query(QUERY_ENERGY_BONUS);
        syncing = true;
        maxMoney.setChecked((flags & 1) != 0);
        freezeMoney.setChecked((flags & 2) != 0);
        fastBuild.setChecked((flags & 4) != 0);
        energyFull.setChecked((flags & 8) != 0);
        energySeek.setProgress(Math.min(ENERGY_MAX_STEPS, bonus / ENERGY_STEP));
        updateEnergyLabel(bonus);
        syncing = false;
    }

    private void updateEnergyLabel(int kilowatts) {
        energyLabel.setText(activity.getString(R.string.cheat_energy_bonus, kilowatts));
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            activity.getResources().getDisplayMetrics());
    }
}
