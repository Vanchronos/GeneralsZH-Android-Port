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


// SIMPLE_TACTICAL_PALETTE_V1
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
import java.util.ArrayList;
import java.util.List;

// GeneralsX @feature Android cheat menu: a Cheat-Engine-style pop-up over the game.
//
// A small draggable CHEAT button floats over the game; tapping it opens/closes the panel.
// Every control here is drawn by hand (colored ON/OFF chips and +/- chips) instead of using the
// stock Switch/SeekBar widgets, which vanished under the app theme on a real device.
// Most cheats affect the local human player; NPC Stop is the explicit single-player exception
// for non-local AI objects. Nothing applies in LAN/online matches or replays.
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
    private static final int ACT_REPAIR_ALL_BUILDINGS = 18;
    private static final int ACT_INSTANT_CAPTURE = 19;
    private static final int SET_BUILD_ANYWHERE = 20;
    private static final int SET_NPC_STOP = 21;
    private static final int SET_AUTO_HEAL_UNITS = 22;
    private static final int SET_AUTO_REPAIR_ALL = 23;
    private static final int ACT_HEAL_REPAIR_ALL = 24;
    private static final int SET_TURRET_FIRE_SPEED = 25;
    private static final int SET_TURRET_RANGE = 26;
    private static final int SET_TURRET_DETECTION = 27;
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

    // Refined tactical palette; gameplay and JNI IDs are unchanged.
    private static final int COLOR_ON = 0xFF269B63;
    private static final int COLOR_OFF = 0xFF536477;
    private static final int COLOR_BUTTON = 0xFF263B50;

    private final Activity activity;
    private final ViewGroup parent;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView toggleButton;
    private ScrollView panelRoot;
    private LinearLayout panel;
    private LinearLayout currentSection;
    private final List<ToggleRow> toggleRows = new ArrayList<>();
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
    private ToggleRow buildAnywhere;
    private ToggleRow npcStop;
    private ToggleRow autoHealUnits;
    private ToggleRow autoRepairAll;
    private ToggleRow turretFireSpeed;
    private ToggleRow turretRange;
    private ToggleRow turretDetection;
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
        // No onDestroy() hook in the activity is needed: stop polling when the button leaves the window.
        toggleButton.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { destroy(); }
        });
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

    /** Adds a collapsible, styled category while preserving the existing cheat controls. */
    private LinearLayout addSection(String titleText, boolean expandedInitially) {
        final LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(2), dp(4), dp(5));
        content.setVisibility(expandedInitially ? View.VISIBLE : View.GONE);

        final boolean[] expanded = new boolean[] { expandedInitially };
        final TextView heading = new TextView(activity);
        heading.setText((expanded[0] ? "▼  " : "▶  ") + titleText);
        heading.setTextColor(0xFFE7F0F8);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(10), dp(8), dp(10), dp(8));
        heading.setMinHeight(dp(36));
        heading.setClickable(true);
        GradientDrawable headerBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] { 0xFF223747, 0xFF192633 });
        headerBg.setCornerRadius(dp(8));
        headerBg.setStroke(dp(1), 0xFF3D6579);
        heading.setBackground(headerBg);
        heading.setElevation(dp(1));

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = dp(5);
        headerParams.bottomMargin = dp(2);
        panel.addView(heading, headerParams);
        panel.addView(content, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        heading.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                expanded[0] = !expanded[0];
                content.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
                heading.setText((expanded[0] ? "▼  " : "▶  ") + titleText);
            }
        });
        return content;
    }

    /** Add a control to the current category; falls back to the panel for top-level controls. */
    private void addCurrentRow(View row) {
        LinearLayout target = currentSection != null ? currentSection : panel;
        target.addView(row);
    }

    /** Turns off toggle controls only. One-shot actions and the energy +/- amount are not executed. */
    private void addDisableAllButton() {
        TextView disableAll = makeChip("DISABLE ALL TOGGLES");
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { 0xFF75404A, 0xFF4A252E });
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), 0xFFC97782);
        disableAll.setBackground(bg);
        disableAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        params.topMargin = dp(9);
        params.bottomMargin = dp(2);
        panel.addView(disableAll, params);
        disableAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (nativeMissing || query(QUERY_AVAILABLE) == 0) {
                    Toast.makeText(activity, R.string.cheat_status_unavailable, Toast.LENGTH_SHORT).show();
                    return;
                }
                for (ToggleRow toggle : toggleRows) toggle.disable();
                Toast.makeText(activity, "All toggles turned OFF", Toast.LENGTH_SHORT).show();
                refresh();
            }
        });
    }

    private void build() {
        toggleButton = new TextView(activity);
        toggleButton.setText("CHEAT");
        toggleButton.setTextColor(Color.WHITE);
        toggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        toggleButton.setTypeface(null, Typeface.BOLD);
        toggleButton.setGravity(Gravity.CENTER);
        toggleButton.setPadding(dp(6), 0, dp(6), 0);
        toggleButton.setAlpha(0.98f);
        GradientDrawable chip = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { 0xF13FAE75, 0xF01B6746 });
        chip.setStroke(dp(1), 0xFF9BE8B8);
        chip.setCornerRadius(dp(10));
        toggleButton.setBackground(chip);
        toggleButton.setElevation(dp(5));
        RelativeLayout.LayoutParams buttonParams = new RelativeLayout.LayoutParams(dp(56), dp(32));
        buttonParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        buttonParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        buttonParams.leftMargin = dp(8);
        buttonParams.topMargin = dp(90);
        installDragAndTap(toggleButton);
        parent.addView(toggleButton, buttonParams);

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable card = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { 0xF01B2938, 0xF00D141E });
        card.setStroke(dp(1), 0xFF49677D);
        card.setCornerRadius(dp(14));
        panel.setBackground(card);
        panel.setElevation(dp(8));
        panel.setClickable(true); // swallow touches so they do not reach the game underneath
        int pad = dp(12);
        panel.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(activity);
        title.setText(R.string.cheat_title);
        title.setTextColor(0xFFF2F7FC);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(null, Typeface.BOLD);
        title.setShadowLayer(1.5f, 0, dp(1), 0x4438D996);
        title.setPadding(dp(2), dp(1), dp(2), dp(3));
        panel.addView(title);

        // A small accent bar polishes the existing header without changing its layout.
        View accent = new View(activity);
        GradientDrawable accentShape = new GradientDrawable();
        accentShape.setColor(0xFF4DD28B);
        accentShape.setCornerRadius(dp(2));
        accent.setBackground(accentShape);
        LinearLayout.LayoutParams accentParams =
            new LinearLayout.LayoutParams(dp(42), dp(3));
        accentParams.bottomMargin = dp(7);
        panel.addView(accent, accentParams);

        statusText = new TextView(activity);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusText.setTextColor(0xFFFFC36A);
        statusText.setPadding(dp(8), dp(5), dp(8), dp(5));
        GradientDrawable statusPill = new GradientDrawable();
        statusPill.setColor(0xFF283342);
        statusPill.setStroke(dp(1), 0xFF405367);
        statusPill.setCornerRadius(dp(8));
        statusText.setBackground(statusPill);
        LinearLayout.LayoutParams statusParams =
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.bottomMargin = dp(7);
        panel.addView(statusText, statusParams);

        // Compact tactical categories. Existing ToggleRow objects and native IDs remain unchanged.
        currentSection = addSection("ECONOMY & POWER", true);
        maxMoney = addToggle(R.string.cheat_max_money, SET_MAX_MONEY);
        freezeMoney = addToggle(R.string.cheat_freeze_money, SET_FREEZE_MONEY);
        energyFull = addToggle(R.string.cheat_energy_full, SET_ENERGY_FULL);
        addEnergyRow();

        currentSection = addSection("BUILD & SUPPORT", false);
        fastBuild = addToggle(R.string.cheat_fast_build, SET_FAST_BUILD);
        noCooldown = addToggle(R.string.cheat_no_cooldown, SET_NO_COOLDOWN);
        buildAnywhere = addToggle(R.string.cheat_build_anywhere, SET_BUILD_ANYWHERE);

        currentSection = addSection("COMBAT ENHANCEMENTS", false);
        damage = addToggle(R.string.cheat_damage, SET_DAMAGE);
        fireRate = addToggle(R.string.cheat_fire_rate, SET_FIRE_RATE);
        tough = addToggle(R.string.cheat_tough, SET_TOUGH);
        speed = addToggle(R.string.cheat_speed, SET_SPEED);
        elite = addToggle(R.string.cheat_elite, SET_ELITE);
        airAmmo = addToggle(R.string.cheat_air_ammo, SET_AIR_AMMO);

        currentSection = addSection("TURRET & WEAPON CONTROL", false);
        turretFireSpeed = addToggle("TURRET FIRE SPEED X3", SET_TURRET_FIRE_SPEED);
        turretRange = addToggle("TURRET RANGE X2", SET_TURRET_RANGE);
        turretDetection = addToggle("TURRET DETECTION X2", SET_TURRET_DETECTION);

        currentSection = addSection("MAP & INFORMATION", false);
        revealMap = addToggle(R.string.cheat_reveal_map, SET_REVEAL_MAP);
        healthBars = addToggle(R.string.cheat_health_bars, SET_HEALTH_BARS);

        currentSection = addSection("UNIT & AI CONTROL", false);
        npcStop = addToggle("NPC STOP (ALLY + ENEMY)", SET_NPC_STOP);
        autoHealUnits = addToggle("AUTO HEAL UNITS", SET_AUTO_HEAL_UNITS);
        autoRepairAll = addToggle("AUTO REPAIR UNITS / BUILDINGS", SET_AUTO_REPAIR_ALL);

        currentSection = addSection("INSTANT ACTIONS", false);
        addActionRow();
        currentSection = null;
        addDisableAllButton();

        TextView note = new TextView(activity);
        note.setText(R.string.cheat_note);
        note.setTextColor(0xFF9AA0A6);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        note.setPadding(0, dp(8), 0, 0);
        panel.addView(note);

        panelRoot = new ScrollView(activity);
        panelRoot.setFillViewport(false);
        panelRoot.setClipToPadding(false);
        panelRoot.setVerticalScrollBarEnabled(false);
        panelRoot.setOverScrollMode(View.OVER_SCROLL_NEVER);
        panelRoot.setBackgroundColor(Color.TRANSPARENT);
        panelRoot.addView(panel);
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.min(dp(310), Math.max(dp(220), screenWidth - dp(86)));
        RelativeLayout.LayoutParams panelParams =
            new RelativeLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
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
        private final int id;
        private boolean on;

        ToggleRow(int labelRes, final int id) { this(activity.getText(labelRes), id); }

        ToggleRow(CharSequence labelText, final int id) {
            this.id = id;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(44));
            row.setPadding(dp(4), dp(1), 0, dp(1));
            row.setClickable(true);

            TextView label = new TextView(activity);
            label.setText(labelText);
            label.setTextColor(0xFFE8EEF5);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            label.setPadding(dp(2), dp(2), dp(4), dp(2));
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            chip = makeChip("OFF");
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            row.addView(chip, new LinearLayout.LayoutParams(dp(60), dp(32)));
            setOn(false);
            toggleRows.add(this);

            View.OnClickListener flip = new View.OnClickListener() {
                @Override public void onClick(View v) {
                    setOn(!on);
                    set(id, on ? 1 : 0);
                }
            };
            row.setOnClickListener(flip);
            chip.setOnClickListener(flip);
            addCurrentRow(row);

            View divider = new View(activity);
            divider.setBackgroundColor(0x334D6479);
            LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            dividerParams.topMargin = dp(1);
            dividerParams.bottomMargin = dp(1);
            panel.addView(divider, dividerParams);
        }

        void setOn(boolean value) {
            on = value;
            chip.setText(value ? "ON" : "OFF");
            setChipColor(chip, value ? COLOR_ON : COLOR_OFF);
        }

        void disable() {
            setOn(false);
            set(id, 0);
        }
    }

    private ToggleRow addToggle(int labelRes, int id) {
        return new ToggleRow(labelRes, id);
    }

    private ToggleRow addToggle(String labelText, int id) {
        return new ToggleRow(labelText, id);
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

        addCurrentRow(row);
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

        addCurrentRow(row);

        // One-shot engine action; no fake toggle/state is shown.
        LinearLayout repairRow = new LinearLayout(activity);
        repairRow.setOrientation(LinearLayout.HORIZONTAL);
        repairRow.setPadding(0, dp(2), 0, dp(2));
        TextView repair = makeChip(activity.getString(R.string.cheat_repair_all_buildings));
        repair.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runAction(ACT_REPAIR_ALL_BUILDINGS, 0); }
        });
        repairRow.addView(repair, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        addCurrentRow(repairRow);

        LinearLayout captureRow = new LinearLayout(activity);
        captureRow.setOrientation(LinearLayout.HORIZONTAL);
        captureRow.setPadding(0, dp(2), 0, dp(2));
        TextView instantCapture = makeChip(activity.getString(R.string.cheat_instant_capture));
        instantCapture.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runAction(ACT_INSTANT_CAPTURE, 0); }
        });
        captureRow.addView(instantCapture, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        addCurrentRow(captureRow);

        LinearLayout healRepairRow = new LinearLayout(activity);
        healRepairRow.setOrientation(LinearLayout.HORIZONTAL);
        healRepairRow.setPadding(0, dp(2), 0, dp(2));
        TextView healRepair = makeChip("HEAL / REPAIR ALL");
        healRepair.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runAction(ACT_HEAL_REPAIR_ALL, 0); }
        });
        healRepairRow.addView(healRepair, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        addCurrentRow(healRepairRow);
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
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setPadding(dp(5), dp(1), dp(5), dp(1));
        t.setClickable(true);
        t.setFocusable(true);
        setChipColor(t, COLOR_BUTTON);
        t.setElevation(dp(1));
        return t;
    }

    private void setChipColor(TextView t, int color) {
        GradientDrawable g = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { lighten(color, 0.12f), darken(color, 0.08f) });
        int stroke = color == COLOR_ON ? 0xFF55C98B
            : (color == COLOR_OFF ? 0xFF71859A : 0xFF40596E);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(9));
        t.setBackground(g);
    }

    private int lighten(int color, float amount) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.rgb(
            Math.min(255, (int) (r + (255 - r) * amount)),
            Math.min(255, (int) (g + (255 - g) * amount)),
            Math.min(255, (int) (b + (255 - b) * amount)));
    }

    private int darken(int color, float amount) {
        return Color.rgb(
            Math.max(0, (int) (Color.red(color) * (1f - amount))),
            Math.max(0, (int) (Color.green(color) * (1f - amount))),
            Math.max(0, (int) (Color.blue(color) * (1f - amount))));
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
        buildAnywhere.setOn((flags & 16384) != 0);
        npcStop.setOn((flags & 32768) != 0);
        autoHealUnits.setOn((flags & 65536) != 0);
        autoRepairAll.setOn((flags & 131072) != 0);
        turretFireSpeed.setOn((flags & 262144) != 0);
        turretRange.setOn((flags & 524288) != 0);
        turretDetection.setOn((flags & 1048576) != 0);
        updateEnergyLabel();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            activity.getResources().getDisplayMetrics());
    }
}
