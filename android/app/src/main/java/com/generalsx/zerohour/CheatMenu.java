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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

// GeneralsX @feature Android cheat menu: a Cheat-Engine-style pop-up over the game.
//
// A small draggable CHEAT button floats over the game; tapping it opens/closes the panel.
// Every control here is drawn by hand (colored ON/OFF chips and +/- chips) instead of using the
// stock Switch/SeekBar widgets, which vanished under the app theme on a real device.
// What the controls do is decided in the engine (Common/GXCheats.cpp): only the human player on
// this device is affected, never an AI player, and nothing applies in LAN/online matches or replays.
// All methods run on the UI thread.
final class CheatMenu {

    private static final String TAG = "GXCheatMenu";

    // Keep in step with the ids in Common/GXCheats.h (the JNI pair is generic).
    private static final int SET_MAX_MONEY = 0;
    private static final int SET_FREEZE_MONEY = 1;
    private static final int SET_FAST_BUILD = 2;
    private static final int SET_ENERGY_FULL = 3;
    private static final int SET_ENERGY_BONUS = 4;
    private static final int SET_NO_COOLDOWN = 5;
    private static final int SET_REVEAL_MAP = 6;
    private static final int SET_HEALTH_BARS = 7;
    private static final int ACT_ADD_GENERAL_POINTS = 8;
    private static final int ACT_MAX_RANK = 9;
    private static final int SET_DAMAGE = 11;
    private static final int SET_FIRE_RATE = 12;
    private static final int SET_TOUGH = 13;
    private static final int SET_SPEED = 14;
    private static final int SET_ELITE = 15;
    private static final int SET_AIR_AMMO = 16;
    private static final int GENERAL_POINTS_PER_TAP = 5;
    private static final int QUERY_AVAILABLE = 0;
    private static final int QUERY_FLAGS = 1;
    private static final int QUERY_ENERGY_BONUS = 2;

    private static final int ENERGY_STEP = 100;  // kW per tap on + / -
    private static final int ENERGY_MAX = 2000;

    private static final int COLOR_ON = 0xFF2E9E50;
    private static final int COLOR_OFF = 0xFF8A3B3B;
    private static final int COLOR_BUTTON = 0xFF3A4A5C;

    private final Activity activity;
    private final ViewGroup parent;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView toggleButton;
    private ScrollView panelRoot;
    private LinearLayout panel;
    private TextView statusText;
    private ToggleRow maxMoney;
    private ToggleRow freezeMoney;
    private ToggleRow fastBuild;
    private ToggleRow energyFull;
    private ToggleRow noCooldown;
    private ToggleRow revealMap;
    private ToggleRow healthBars;
    private ToggleRow damage;
    private ToggleRow fireRate;
    private ToggleRow tough;
    private ToggleRow speed;
    private ToggleRow elite;
    private ToggleRow airAmmo;
    private TextView energyLabel;
    private int energyBonus;
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

    // ---- native bridge ----
    // If the engine library lacks the JNI functions we keep the menu visible and SAY so in the
    // panel (and in logcat, tag GXCheatMenu) instead of hiding it: a hidden menu hides the bug.

    private void markNativeMissing(UnsatisfiedLinkError e) {
        if (!nativeMissing) {
            Log.e(TAG, "Cheat JNI functions not found in the loaded engine library", e);
        }
        nativeMissing = true;
    }

    private void set(int id, int value) {
        if (nativeMissing) {
            return;
        }
        try {
            GeneralsZHActivity.nativeCheatSet(id, value);
        } catch (UnsatisfiedLinkError e) {
            markNativeMissing(e);
        }
    }

    private int query(int id) {
        if (nativeMissing) {
            return 0;
        }
        try {
            return GeneralsZHActivity.nativeCheatQuery(id);
        } catch (UnsatisfiedLinkError e) {
            markNativeMissing(e);
            return 0;
        }
    }

    // ---- UI ----

    private void build() {
        toggleButton = new TextView(activity);
        toggleButton.setText("CHEAT");
        toggleButton.setTextColor(Color.WHITE);
        toggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        toggleButton.setTypeface(null, Typeface.BOLD);
        toggleButton.setGravity(Gravity.CENTER);
        toggleButton.setAlpha(0.8f);
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
        card.setColor(0xF01B1F24);
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
        title.setTypeface(null, Typeface.BOLD);
        panel.addView(title);

        statusText = new TextView(activity);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusText.setTextColor(0xFFFFB74D);
        statusText.setPadding(0, dp(2), 0, dp(6));
        panel.addView(statusText);

        maxMoney = addToggle(R.string.cheat_max_money, SET_MAX_MONEY);
        freezeMoney = addToggle(R.string.cheat_freeze_money, SET_FREEZE_MONEY);
        fastBuild = addToggle(R.string.cheat_fast_build, SET_FAST_BUILD);
        energyFull = addToggle(R.string.cheat_energy_full, SET_ENERGY_FULL);
        addEnergyRow();
        noCooldown = addToggle(R.string.cheat_no_cooldown, SET_NO_COOLDOWN);
        revealMap = addToggle(R.string.cheat_reveal_map, SET_REVEAL_MAP);
        healthBars = addToggle(R.string.cheat_health_bars, SET_HEALTH_BARS);
        damage = addToggle(R.string.cheat_damage, SET_DAMAGE);
        fireRate = addToggle(R.string.cheat_fire_rate, SET_FIRE_RATE);
        tough = addToggle(R.string.cheat_tough, SET_TOUGH);
        speed = addToggle(R.string.cheat_speed, SET_SPEED);
        elite = addToggle(R.string.cheat_elite, SET_ELITE);
        airAmmo = addToggle(R.string.cheat_air_ammo, SET_AIR_AMMO);
        addActionRow();

        TextView note = new TextView(activity);
        note.setText(R.string.cheat_note);
        note.setTextColor(0xFF9AA0A6);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        note.setPadding(0, dp(8), 0, 0);
        panel.addView(note);

        panelRoot = new ScrollView(activity);
        panelRoot.addView(panel);
        RelativeLayout.LayoutParams panelParams =
            new RelativeLayout.LayoutParams(dp(310), ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        panelParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        panelParams.leftMargin = dp(70);
        panelParams.topMargin = dp(20);
        panelParams.bottomMargin = dp(20);
        panelRoot.setVisibility(View.GONE);
        parent.addView(panelRoot, panelParams);

        updateEnergyLabel();
        refresh();
    }

    /** One row: a label and a big colored ON / OFF chip. Tap anywhere on the row to flip it. */
    private final class ToggleRow {
        private final TextView chip;
        private boolean on;

        ToggleRow(int labelRes, final int id) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(44));
            row.setClickable(true);

            TextView label = new TextView(activity);
            label.setText(labelRes);
            label.setTextColor(Color.WHITE);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            chip = makeChip("OFF");
            row.addView(chip, new LinearLayout.LayoutParams(dp(60), dp(32)));
            setOn(false);

            View.OnClickListener flip = new View.OnClickListener() {
                @Override public void onClick(View v) {
                    setOn(!on);
                    set(id, on ? 1 : 0);
                }
            };
            row.setOnClickListener(flip);
            chip.setOnClickListener(flip);
            panel.addView(row);
        }

        void setOn(boolean value) {
            on = value;
            chip.setText(value ? "ON" : "OFF");
            setChipColor(chip, value ? COLOR_ON : COLOR_OFF);
        }
    }

    private ToggleRow addToggle(int labelRes, int id) {
        return new ToggleRow(labelRes, id);
    }

    /** Extra power: label with  [-]  [+]  chips, 100 kW per tap. */
    private void addEnergyRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));

        energyLabel = new TextView(activity);
        energyLabel.setTextColor(Color.WHITE);
        energyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(energyLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView minus = makeChip("-");
        setChipColor(minus, COLOR_BUTTON);
        minus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { changeEnergy(-ENERGY_STEP); }
        });
        LinearLayout.LayoutParams minusParams = new LinearLayout.LayoutParams(dp(40), dp(32));
        minusParams.rightMargin = dp(6);
        row.addView(minus, minusParams);

        TextView plus = makeChip("+");
        setChipColor(plus, COLOR_BUTTON);
        plus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { changeEnergy(ENERGY_STEP); }
        });
        row.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(32)));

        panel.addView(row);
    }

    /** Two one-shot buttons: add Generals points, and jump to the highest rank. */
    private void addActionRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(2));

        TextView points = makeChip(activity.getString(R.string.cheat_add_points, GENERAL_POINTS_PER_TAP));
        points.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runAction(ACT_ADD_GENERAL_POINTS, GENERAL_POINTS_PER_TAP); }
        });
        LinearLayout.LayoutParams pointsParams = new LinearLayout.LayoutParams(0, dp(36), 1f);
        pointsParams.rightMargin = dp(6);
        row.addView(points, pointsParams);

        TextView rank = makeChip(activity.getString(R.string.cheat_max_rank));
        rank.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runAction(ACT_MAX_RANK, 0); }
        });
        row.addView(rank, new LinearLayout.LayoutParams(0, dp(36), 1f));

        panel.addView(row);
    }

    /** A one-shot action; the engine applies it on its own thread. Tells the player if it could not run. */
    private void runAction(int id, int value) {
        boolean available = query(QUERY_AVAILABLE) != 0;
        if (nativeMissing || !available) {
            Toast.makeText(activity, R.string.cheat_status_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        set(id, value);
        Toast.makeText(activity, R.string.cheat_action_sent, Toast.LENGTH_SHORT).show();
    }

    private void changeEnergy(int delta) {
        energyBonus = Math.max(0, Math.min(ENERGY_MAX, energyBonus + delta));
        set(SET_ENERGY_BONUS, energyBonus);
        updateEnergyLabel();
    }

    private void updateEnergyLabel() {
        energyLabel.setText(activity.getString(R.string.cheat_energy_bonus, energyBonus));
    }

    private TextView makeChip(String text) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setClickable(true);
        setChipColor(t, COLOR_BUTTON);
        return t;
    }

    private void setChipColor(TextView t, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(6));
        t.setBackground(g);
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

    /** Reads the engine's current state into the controls (and tells if a game is running). */
    private void refresh() {
        int available = query(QUERY_AVAILABLE);
        toggleButton.setAlpha(nativeMissing ? 0.45f : (available != 0 ? 0.95f : 0.6f));
        if (!isPanelShown()) {
            return;
        }
        if (nativeMissing) {
            statusText.setText(R.string.cheat_status_no_native);
            statusText.setTextColor(0xFFEF5350);
            return;
        }
        statusText.setText(available != 0 ? R.string.cheat_status_ready : R.string.cheat_status_unavailable);
        statusText.setTextColor(available != 0 ? 0xFF7FD99A : 0xFFFFB74D);
        int flags = query(QUERY_FLAGS);
        energyBonus = query(QUERY_ENERGY_BONUS);
        maxMoney.setOn((flags & 1) != 0);
        freezeMoney.setOn((flags & 2) != 0);
        fastBuild.setOn((flags & 4) != 0);
        energyFull.setOn((flags & 8) != 0);
        noCooldown.setOn((flags & 16) != 0);
        revealMap.setOn((flags & 32) != 0);
        healthBars.setOn((flags & 64) != 0);
        damage.setOn((flags & 256) != 0);
        fireRate.setOn((flags & 512) != 0);
        tough.setOn((flags & 1024) != 0);
        speed.setOn((flags & 2048) != 0);
        elite.setOn((flags & 4096) != 0);
        airAmmo.setOn((flags & 8192) != 0);
        updateEnergyLabel();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            activity.getResources().getDisplayMetrics());
    }
}
