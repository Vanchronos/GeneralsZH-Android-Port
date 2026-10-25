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
    // CYBER_HYBRID_UI_V2_APPLIED: presentation-only upgrade.

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

    // CYBER_HYBRID_UI_V2 palette: cyan glass, deep navy, active neon green.
    private static final int COLOR_ON = 0xFF17A875;
    private static final int COLOR_OFF = 0xFF34465B;
    private static final int COLOR_BUTTON = 0xFF17364D;
    private static final int COLOR_CYAN = 0xFF24CFE8;
    private static final int COLOR_ACTION = 0xFF126C89;

    private final Activity activity;
    private final ViewGroup parent;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView toggleButton;
    private ScrollView panelRoot;
    private LinearLayout panel;
    private LinearLayout currentSection;
    private TextView activeCountText;
    private TextView minimizeButton;
    private TextView disableAllButton;
    private TextView noteText;
    private int expandedPanelHeight;
    private boolean compactMode;
    private final List<LinearLayout> sectionContents = new ArrayList<>();
    private final List<TextView> sectionHeadings = new ArrayList<>();
    private final List<boolean[]> sectionExpandedStates = new ArrayList<>();
    private final List<String> sectionTitles = new ArrayList<>();
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
        sectionContents.add(content);
        sectionExpandedStates.add(expanded);
        sectionTitles.add(titleText);
        final TextView heading = new TextView(activity);
        sectionHeadings.add(heading);
        heading.setText((expanded[0] ? "▾  " : "▸  ") + titleText.toUpperCase(java.util.Locale.ROOT));
        heading.setTextColor(0xFF8DEEFF);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(12), dp(8), dp(12), dp(8));
        heading.setMinHeight(dp(40));
        heading.setClickable(true);
        GradientDrawable headerBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] { 0xFF173D54, 0xFF142B3D, 0xFF101B2B });
        headerBg.setCornerRadius(dp(9));
        headerBg.setStroke(dp(1), 0xFF29CFE8);
        heading.setBackground(headerBg);
        heading.setElevation(dp(2));

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
                heading.setText((expanded[0] ? "▾  " : "▸  ") + titleText.toUpperCase(java.util.Locale.ROOT));
            }
        });
        return content;
    }

    /** Add a control to the current category; falls back to the panel for top-level controls. */
    private void addCurrentRow(View row) {
        LinearLayout target = currentSection != null ? currentSection : panel;
        target.addView(row);
    }

    private void addCurrentRow(View row, LinearLayout.LayoutParams params) {
        LinearLayout target = currentSection != null ? currentSection : panel;
        target.addView(row, params);
    }

    /** Turns off toggle controls only. One-shot actions and the energy +/- amount are not executed. */
    private void addDisableAllButton() {
        TextView disableAll = makeChip("DISABLE ALL TOGGLES");
        disableAllButton = disableAll;
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

    private void styleHeaderButton(TextView button, int fill, int stroke) {
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { lighten(fill, 0.14f), darken(fill, 0.12f) });
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), stroke);
        button.setBackground(background);
        button.setTextColor(0xFFF4FBFF);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setElevation(dp(2));
    }

    private void updateActiveCount() {
        int active = 0;
        for (ToggleRow row : toggleRows) if (row.isOn()) active++;
        if (activeCountText != null) {
            activeCountText.setText("ACTIVE  " + active + "/" + toggleRows.size());
        }
        if (toggleButton != null) {
            GradientDrawable floating = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                active > 0
                    ? new int[] { 0xF12ADCA0, 0xF012795C }
                    : new int[] { 0xF12AD7EE, 0xF015597A });
            floating.setCornerRadius(dp(12));
            floating.setStroke(dp(1), active > 0 ? 0xFF8BFFD0 : 0xFF7AF1FF);
            toggleButton.setBackground(floating);
            toggleButton.setElevation(dp(active > 0 ? 9 : 6));
        }
    }

    private void setCompactMode(boolean compact) {
        compactMode = compact;
        for (int i = 0; i < sectionContents.size(); i++) {
            if (compactMode) {
                sectionHeadings.get(i).setVisibility(View.GONE);
                sectionContents.get(i).setVisibility(View.GONE);
            } else {
                sectionHeadings.get(i).setVisibility(View.VISIBLE);
                sectionContents.get(i).setVisibility(
                    sectionExpandedStates.get(i)[0] ? View.VISIBLE : View.GONE);
            }
        }
        if (disableAllButton != null) disableAllButton.setVisibility(compactMode ? View.GONE : View.VISIBLE);
        if (noteText != null) noteText.setVisibility(compactMode ? View.GONE : View.VISIBLE);
        if (minimizeButton != null) {
            minimizeButton.setText(compactMode ? "+" : "−");
            minimizeButton.setContentDescription(compactMode ? "Restore cheat panel" : "Minimize cheat panel");
        }
        if (panelRoot != null) {
            ViewGroup.LayoutParams raw = panelRoot.getLayoutParams();
            if (raw instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) raw;
                params.height = compactMode ? dp(146) : expandedPanelHeight;
                panelRoot.setLayoutParams(params);
            }
            panelRoot.scrollTo(0, 0);
            panelRoot.requestLayout();
        }
    }

    private void installPanelDrag(final View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startLeft, startTop;
            private boolean moved;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (panelRoot == null || !(panelRoot.getLayoutParams() instanceof RelativeLayout.LayoutParams))
                    return false;
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) panelRoot.getLayoutParams();
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY();
                        startLeft = params.leftMargin; startTop = params.topMargin;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
                        if (moved) {
                            int maxLeft = Math.max(0, parent.getWidth() - panelRoot.getWidth());
                            int maxTop = Math.max(0, parent.getHeight() - panelRoot.getHeight());
                            params.leftMargin = Math.max(0, Math.min(maxLeft, startLeft + (int) dx));
                            params.topMargin = Math.max(0, Math.min(maxTop, startTop + (int) dy));
                            panelRoot.setLayoutParams(params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void build() {
        toggleButton = new TextView(activity);
        toggleButton.setText("◆ CHEAT");
        toggleButton.setTextColor(Color.WHITE);
        toggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        toggleButton.setTypeface(null, Typeface.BOLD);
        toggleButton.setGravity(Gravity.CENTER);
        toggleButton.setPadding(dp(6), 0, dp(6), 0);
        toggleButton.setAlpha(0.98f);
        GradientDrawable chip = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { 0xF12AD7EE, 0xF015597A });
        chip.setStroke(dp(1), 0xFF7AF1FF);
        chip.setCornerRadius(dp(12));
        toggleButton.setBackground(chip);
        toggleButton.setElevation(dp(8));
        RelativeLayout.LayoutParams buttonParams = new RelativeLayout.LayoutParams(dp(88), dp(36));
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
            new int[] { 0xF21A2940, 0xF10A1222 });
        card.setStroke(dp(1), 0xFF2BCFEA);
        card.setCornerRadius(dp(16));
        panel.setBackground(card);
        panel.setElevation(dp(8));
        panel.setClickable(true); // swallow touches so they do not reach the game underneath
        int pad = dp(14);
        panel.setPadding(pad, pad, pad, pad);

        // CYBER_HYBRID_UI_V2: branded, draggable header and compact control buttons.
        final LinearLayout dragHandle = new LinearLayout(activity);
        dragHandle.setOrientation(LinearLayout.VERTICAL);
        dragHandle.setGravity(Gravity.CENTER_VERTICAL);
        dragHandle.setPadding(dp(2), dp(1), dp(2), dp(1));
        dragHandle.setClickable(true);

        TextView title = new TextView(activity);
        title.setText("GENERALS X");
        title.setTextColor(0xFFF4FBFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setShadowLayer(4f, 0, 0, 0x5531DDF4);
        title.setLetterSpacing(0.04f);
        dragHandle.addView(title, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(activity);
        subtitle.setText("TACTICAL COMMAND  //  CYBER-HYBRID");
        subtitle.setTextColor(0xFF71D9EC);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        subtitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        subtitle.setPadding(0, dp(1), 0, 0);
        dragHandle.addView(subtitle, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dragParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(dragHandle, dragParams);

        LinearLayout headerControls = new LinearLayout(activity);
        headerControls.setOrientation(LinearLayout.HORIZONTAL);
        headerControls.setGravity(Gravity.CENTER_VERTICAL);
        minimizeButton = makeChip("−");
        minimizeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        styleHeaderButton(minimizeButton, COLOR_CYAN, 0xFF2BD5EC);
        headerControls.addView(minimizeButton, new LinearLayout.LayoutParams(dp(34), dp(32)));
        TextView closeButton = makeChip("×");
        closeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        styleHeaderButton(closeButton, 0xFF963D59, 0xFFFF86A5);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(34), dp(32));
        closeParams.leftMargin = dp(5);
        headerControls.addView(closeButton, closeParams);
        topBar.addView(headerControls, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(topBar);

        View accent = new View(activity);
        GradientDrawable accentShape = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] { 0xFF2EE6F4, 0xFF31D59B, 0x0031D59B });
        accentShape.setCornerRadius(dp(2));
        accent.setBackground(accentShape);
        LinearLayout.LayoutParams accentParams =
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        accentParams.topMargin = dp(7);
        accentParams.bottomMargin = dp(8);
        panel.addView(accent, accentParams);

        LinearLayout statusRow = new LinearLayout(activity);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusText = new TextView(activity);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        statusText.setTextColor(0xFFFFC36A);
        statusText.setMaxLines(2);
        statusText.setPadding(dp(9), dp(7), dp(9), dp(7));
        GradientDrawable statusPill = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] { 0xFF1A3448, 0xFF182333 });
        statusPill.setStroke(dp(1), 0xFF3A687D);
        statusPill.setCornerRadius(dp(8));
        statusText.setBackground(statusPill);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        activeCountText = makeChip("ACTIVE  0/0");
        activeCountText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        setChipColor(activeCountText, COLOR_ACTION);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(dp(96), dp(30));
        countParams.leftMargin = dp(6);
        statusRow.addView(activeCountText, countParams);
        LinearLayout.LayoutParams statusRowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusRowParams.bottomMargin = dp(7);
        panel.addView(statusRow, statusRowParams);

        minimizeButton.setContentDescription("Minimize or restore cheat panel");
        minimizeButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setCompactMode(!compactMode); }
        });
        closeButton.setContentDescription("Close cheat panel");
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closePanel(); }
        });


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

        noteText = new TextView(activity);
        noteText.setText("GX // TACTICAL OVERLAY  •  SINGLE PLAYER");
        noteText.setTextColor(0xFF6C9CAA);
        noteText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        noteText.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        noteText.setPadding(dp(2), dp(9), dp(2), dp(2));
        panel.addView(noteText);

        panelRoot = new ScrollView(activity);
        panelRoot.setFillViewport(false);
        panelRoot.setClipToPadding(false);
        panelRoot.setVerticalScrollBarEnabled(false);
        panelRoot.setOverScrollMode(View.OVER_SCROLL_NEVER);
        panelRoot.setBackgroundColor(Color.TRANSPARENT);
        panelRoot.addView(panel);
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int panelWidth = Math.min(dp(430), Math.max(dp(250), screenWidth - dp(100)));
        expandedPanelHeight = Math.min(dp(620), Math.max(dp(250), screenHeight - dp(48)));
        RelativeLayout.LayoutParams panelParams =
            new RelativeLayout.LayoutParams(panelWidth, expandedPanelHeight);
        panelParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        panelParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        panelParams.leftMargin = dp(70);
        panelParams.topMargin = dp(16);
        panelParams.bottomMargin = dp(16);
        panelRoot.setVisibility(View.GONE);
        parent.addView(panelRoot, panelParams);
        installPanelDrag(dragHandle);
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
            row.setPadding(dp(8), dp(3), dp(6), dp(3));
            GradientDrawable rowCard = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { 0x2032A9CC, 0x120C1C2B });
            rowCard.setCornerRadius(dp(8));
            rowCard.setStroke(dp(1), 0x243BC9E8);
            row.setBackground(rowCard);
            row.setClickable(true);

            TextView label = new TextView(activity);
            label.setText(labelText);
            label.setTextColor(0xFFE8EEF5);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            label.setPadding(dp(2), dp(2), dp(4), dp(2));
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            chip = makeChip("OFF");
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            row.addView(chip, new LinearLayout.LayoutParams(dp(76), dp(32)));
            setOn(false);
            toggleRows.add(this);

            View.OnClickListener flip = new View.OnClickListener() {
                @Override public void onClick(View v) {
                    setOn(!on);
                    set(id, on ? 1 : 0);
                    updateActiveCount();
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
            addCurrentRow(divider, dividerParams);
        }

        void setOn(boolean value) {
            on = value;
            chip.setText(value ? "ACTIVE" : "OFF");
            setChipColor(chip, value ? COLOR_ON : COLOR_OFF);
            chip.setElevation(dp(value ? 4 : 1));
        }

        void disable() {
            setOn(false);
            set(id, 0);
        }

        boolean isOn() { return on; }
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
        setChipColor(points, COLOR_ACTION);
        points.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runAction(ACT_ADD_GENERAL_POINTS, GENERAL_POINTS_PER_TAP); }
        });
        LinearLayout.LayoutParams pointsParams = new LinearLayout.LayoutParams(0, dp(36), 1f);
        pointsParams.rightMargin = dp(6);
        row.addView(points, pointsParams);

        TextView rank = makeChip(activity.getString(R.string.cheat_max_rank));
        setChipColor(rank, COLOR_ACTION);
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
        setChipColor(repair, COLOR_ACTION);
        repair.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runAction(ACT_REPAIR_ALL_BUILDINGS, 0); }
        });
        repairRow.addView(repair, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        addCurrentRow(repairRow);

        LinearLayout captureRow = new LinearLayout(activity);
        captureRow.setOrientation(LinearLayout.HORIZONTAL);
        captureRow.setPadding(0, dp(2), 0, dp(2));
        TextView instantCapture = makeChip(activity.getString(R.string.cheat_instant_capture));
        setChipColor(instantCapture, COLOR_ACTION);
        instantCapture.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runAction(ACT_INSTANT_CAPTURE, 0); }
        });
        captureRow.addView(instantCapture, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        addCurrentRow(captureRow);

        LinearLayout healRepairRow = new LinearLayout(activity);
        healRepairRow.setOrientation(LinearLayout.HORIZONTAL);
        healRepairRow.setPadding(0, dp(2), 0, dp(2));
        TextView healRepair = makeChip("HEAL / REPAIR ALL");
        setChipColor(healRepair, COLOR_ACTION);
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
        int stroke = color == COLOR_ON ? 0xFF59F0B1
            : (color == COLOR_OFF ? 0xFF71889E
            : (color == COLOR_ACTION ? 0xFF45DFF4 : 0xFF3C7A97));
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
                            int maxLeft = Math.max(0, parent.getWidth() - v.getWidth());
                            int maxTop = Math.max(0, parent.getHeight() - v.getHeight());
                            lp.leftMargin = Math.max(0, Math.min(maxLeft, (int) (startLeft + dx)));
                            lp.topMargin = Math.max(0, Math.min(maxTop, (int) (startTop + dy)));
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
        updateActiveCount();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            activity.getResources().getDisplayMetrics());
    }
}
