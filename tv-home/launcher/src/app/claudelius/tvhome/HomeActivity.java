package app.claudelius.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Apple TV-style home screen: full-screen rotating photo, clock, and a dock of favourite
 * apps. "All apps" opens a grid; long-press OK (or the Menu key) on any tile for options.
 */
public class HomeActivity extends Activity {
    private static final String ACTION_APPS = "@apps", ACTION_SETTINGS = "@settings";
    private static final float FOCUS_SCALE = 1.12f;

    private DockStore dock;
    private Wallpapers wallpapers;
    private FrameLayout root;
    private LinearLayout dockRow;
    private ScrollView appsOverlay;
    private LinearLayout appsGrid;
    private final Map<String, AppEntry> installed = new HashMap<>();
    private List<AppEntry> allApps = new ArrayList<>();
    private String lastDockFocus;
    private boolean appsDirty = true;
    private int screenW;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            appsDirty = true;
            reloadApps();
            buildDock();
            // Rebuilding removes the focused view; give focus back so the remote keeps working.
            if (isAppsOpen()) { buildAppsGrid(); focusFirstApp(); } else focusDock(lastDockFocus);
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dock = new DockStore(this);
        screenW = getResources().getDisplayMetrics().widthPixels;

        root = new FrameLayout(this);
        root.setClipChildren(false);
        setContentView(root);

        // Wallpaper layer: gradient fallback + two image views for cross-fading.
        FrameLayout wall = new FrameLayout(this);
        wall.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0E2233, 0xFF1F4A5A, 0xFF0B1A12}));
        ImageView a = wallpaperView(), b = wallpaperView();
        wall.addView(a, match());
        wall.addView(b, match());
        root.addView(wall, match());

        // Legibility scrims: darker at the bottom (dock) and top (clock).
        View bottomScrim = new View(this);
        bottomScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xB0000000, 0x00000000}));
        root.addView(bottomScrim, new FrameLayout.LayoutParams(-1, (int) (getResources()
                .getDisplayMetrics().heightPixels * 0.55f), Gravity.BOTTOM));
        View topScrim = new View(this);
        topScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x80000000, 0x00000000}));
        root.addView(topScrim, new FrameLayout.LayoutParams(-1, dp(160), Gravity.TOP));

        // Photo caption, top-left (like the Apple TV aerial location label).
        TextView credit = text(13, false);
        credit.setAlpha(0f);
        credit.setMaxWidth(screenW / 2);
        credit.setSingleLine(true);
        credit.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        clp.setMargins(dp(48), dp(36), 0, 0);
        root.addView(credit, clp);

        // Clock, top-right.
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        styleText(time, 44, false);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d. MMMM");
        date.setFormat12Hour("EEEE, MMMM d");
        styleText(date, 16, false);
        date.setAlpha(0.85f);
        clock.addView(time);
        clock.addView(date);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        tlp.setMargins(0, dp(28), dp(48), 0);
        root.addView(clock, tlp);

        // Dock.
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        dlp.setMargins(dp(40), 0, dp(40), dp(48));
        root.addView(dockRow, dlp);

        // All-apps overlay.
        appsOverlay = new ScrollView(this);
        appsOverlay.setBackground(new ColorDrawable(0xE6101418));
        appsOverlay.setClipChildren(false);
        appsOverlay.setVisibility(View.GONE);
        appsOverlay.setFillViewport(true);
        appsGrid = new LinearLayout(this);
        appsGrid.setOrientation(LinearLayout.VERTICAL);
        appsGrid.setClipChildren(false);
        appsGrid.setClipToPadding(false);
        appsGrid.setPadding(dp(64), dp(40), dp(64), dp(48));
        appsOverlay.addView(appsGrid, new FrameLayout.LayoutParams(-1, -2));
        root.addView(appsOverlay, match());

        wallpapers = new Wallpapers(this, a, b, credit);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);

        reloadApps();
        buildDock();
    }

    @Override protected void onStart() {
        super.onStart();
        wallpapers.start();
    }

    @Override protected void onResume() {
        super.onResume();
        if (appsDirty) { reloadApps(); buildDock(); }
        if (!isAppsOpen()) focusDock(lastDockFocus);
    }

    @Override protected void onStop() {
        wallpapers.stop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        wallpapers.destroy();
        super.onDestroy();
    }

    /** Home pressed while already home: close the grid and jump back to the first dock tile. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        closeApps();
        focusDock(null);
    }

    @Override public void onBackPressed() {
        if (isAppsOpen()) closeApps();
        // A home screen never finishes itself.
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View v = getCurrentFocus();
            if (v != null && v.getTag() instanceof String) { showOptions((String) v.getTag()); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------ data

    private void reloadApps() {
        allApps = AppEntry.queryAll(this);
        installed.clear();
        for (AppEntry e : allApps) installed.put(e.pkg, e);
        installed.put(AppEntry.HDMI, new AppEntry(AppEntry.HDMI,
                Text.t(this, "HDMI-Eingänge", "HDMI inputs"), null, true));
        appsDirty = false;
    }

    // ------------------------------------------------------------------ dock

    private void buildDock() {
        dockRow.removeAllViews();
        List<AppEntry> items = new ArrayList<>();
        for (String p : dock.load()) {
            AppEntry e = installed.get(p);
            if (e != null) items.add(e);
        }
        // Favourites are 16:9 cards; "All apps" is a square card of the same height.
        // Settings lives in the All-apps grid so the favourites can be bigger.
        int n = items.size() + 1;
        int gap = dp(18);
        int avail = screenW - dp(80) - gap * (n - 1);
        float units = items.size() + 0.5625f;
        int w = Math.min(dp(170), (int) (avail / Math.max(units, 1f)));
        int h = w * 9 / 16;

        for (AppEntry e : items) dockRow.addView(appTile(e, w, h, false), tileLp(gap));
        dockRow.addView(actionTile(ACTION_APPS, Glyph.APPS,
                Text.t(this, "Alle Apps", "All apps"), h, h, false), tileLp(0));
    }

    private void focusDock(String tag) {
        View target = tag == null ? null : dockRow.findViewWithTag(tag);
        if (target == null && dockRow.getChildCount() > 0) target = dockRow.getChildAt(0);
        if (target != null) target.requestFocus();
    }

    // ------------------------------------------------------------------ all apps

    private boolean isAppsOpen() {
        return appsOverlay.getVisibility() == View.VISIBLE;
    }

    private void openApps() {
        if (appsDirty) reloadApps();
        buildAppsGrid();
        appsOverlay.setAlpha(0f);
        appsOverlay.setVisibility(View.VISIBLE);
        appsOverlay.animate().alpha(1f).setDuration(180).start();
        dockRow.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        appsOverlay.scrollTo(0, 0);
        focusFirstApp();
    }

    private void focusFirstApp() {
        if (appsGrid.getChildCount() > 1) {
            LinearLayout row = (LinearLayout) appsGrid.getChildAt(1);
            if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
        }
    }

    private void closeApps() {
        if (!isAppsOpen()) return;
        appsOverlay.setVisibility(View.GONE);
        appsGrid.removeAllViews(); // release banners while hidden
        dockRow.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        focusDock(lastDockFocus);
    }

    private void buildAppsGrid() {
        appsGrid.removeAllViews();
        TextView title = text(28, true);
        title.setText(Text.t(this, "Apps", "Apps"));
        title.setPadding(0, 0, 0, dp(24));
        appsGrid.addView(title);

        int cols = 5, gap = dp(28);
        int w = (screenW - dp(128) - gap * (cols - 1)) / cols;
        int h = w * 9 / 16;

        List<View> tiles = new ArrayList<>();
        tiles.add(appTile(installed.get(AppEntry.HDMI), w, h, true));
        tiles.add(actionTile(ACTION_SETTINGS, Glyph.SETTINGS,
                Text.t(this, "Einstellungen", "Settings"), w, h, true));
        for (AppEntry e : allApps) tiles.add(appTile(e, w, h, true));

        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.bottomMargin = dp(18);
                appsGrid.addView(row, rlp);
            }
            row.addView(tiles.get(i), tileLp(i % cols == cols - 1 ? 0 : gap));
        }
    }

    // ------------------------------------------------------------------ tiles

    private View appTile(final AppEntry e, int w, int h, boolean alwaysLabel) {
        FrameLayout card = card(w, h);
        if (e.isHdmi()) {
            TextView t = text(Math.max(14, (int) (h / getResources().getDisplayMetrics().density * 0.28f)), true);
            t.setText("HDMI");
            t.setGravity(Gravity.CENTER);
            t.setLetterSpacing(0.08f);
            card.addView(t, match());
        } else {
            e.loadArt(getPackageManager());
            ImageView iv = new ImageView(this);
            Drawable banner = e.banner();
            if (banner != null) {
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageDrawable(banner);
                card.addView(iv, match());
            } else if (e.icon() != null) {
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageDrawable(e.icon());
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h * 3 / 5, h * 3 / 5, Gravity.CENTER);
                card.addView(iv, lp);
            }
        }
        return tile(e.pkg, e.label, card, alwaysLabel, new Runnable() {
            @Override public void run() { launch(e); }
        });
    }

    private View actionTile(final String tag, int glyph, String label, int w, int h, boolean alwaysLabel) {
        FrameLayout card = card(w, h);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Glyph(glyph));
        card.addView(iv, match());
        return tile(tag, label, card, alwaysLabel, new Runnable() {
            @Override public void run() {
                if (ACTION_APPS.equals(tag)) openApps();
                else safeStart(new Intent(Settings.ACTION_SETTINGS));
            }
        });
    }

    private FrameLayout card(int w, int h) {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x40FFFFFF);
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12));
            }
        });
        card.setClipToOutline(true);
        card.setElevation(dp(2));
        card.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return card;
    }

    /** Wraps a card with a label; focus scales the card up with a shadow, like tvOS. */
    private View tile(final String tag, String label, final FrameLayout card, final boolean alwaysLabel,
                      final Runnable onClick) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setClipChildren(false);
        t.setClipToPadding(false);
        t.setFocusable(true);
        t.setClickable(true);
        t.setLongClickable(true);
        t.setTag(tag);
        t.addView(card);

        final TextView name = text(14, false);
        name.setText(label);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setAlpha(alwaysLabel ? 0.8f : 0f);
        // The label may be wider than a small card; negative margins let it overhang
        // without changing the tile's width (so dock spacing stays even).
        int cardW = card.getLayoutParams().width;
        int labelW = Math.max(cardW, dp(120));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(labelW, -2);
        nlp.leftMargin = nlp.rightMargin = -(labelW - cardW) / 2;
        nlp.topMargin = dp(14);
        t.addView(name, nlp);

        final DecelerateInterpolator ease = new DecelerateInterpolator();
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                card.animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f)
                        .translationZ(has ? dp(14) : 0).setInterpolator(ease).setDuration(150).start();
                name.animate().alpha(has ? 1f : (alwaysLabel ? 0.8f : 0f)).setDuration(150).start();
                if (has && v.getParent() == dockRow) lastDockFocus = tag;
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onClick.run(); }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showOptions(tag); return true; }
        });
        return t;
    }

    // ------------------------------------------------------------------ actions

    private void launch(AppEntry e) {
        if (e.isHdmi()) { Hdmi.open(this); return; }
        safeStart(e.launchIntent());
    }

    private void safeStart(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(this, Text.t(this, "Kann nicht geöffnet werden", "Can't open this"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showOptions(final String pkg) {
        if (ACTION_APPS.equals(pkg) || ACTION_SETTINGS.equals(pkg)) return;
        final boolean inDock = dock.contains(pkg);
        final boolean fromDock = !isAppsOpen();
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        if (fromDock && inDock) {
            labels.add(Text.t(this, "Nach links", "Move left"));
            actions.add(new Runnable() { @Override public void run() { dock.move(pkg, -1); buildDock(); focusDock(pkg); } });
            labels.add(Text.t(this, "Nach rechts", "Move right"));
            actions.add(new Runnable() { @Override public void run() { dock.move(pkg, 1); buildDock(); focusDock(pkg); } });
        }
        if (inDock) {
            labels.add(Text.t(this, "Aus dem Dock entfernen", "Remove from dock"));
            actions.add(new Runnable() { @Override public void run() { dock.remove(pkg); buildDock(); if (!isAppsOpen()) focusDock(null); } });
        } else {
            labels.add(Text.t(this, "Zum Dock hinzufügen", "Add to dock"));
            actions.add(new Runnable() { @Override public void run() { dock.add(pkg); buildDock(); } });
        }
        if (!AppEntry.HDMI.equals(pkg)) {
            labels.add(Text.t(this, "App-Info", "App info"));
            actions.add(new Runnable() { @Override public void run() {
                safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", pkg, null)));
            } });
        }
        AppEntry e = installed.get(pkg);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(e == null ? pkg : e.label)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { actions.get(which).run(); }
                })
                .show();
    }

    // ------------------------------------------------------------------ helpers

    private ImageView wallpaperView() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return iv;
    }

    private TextView text(int sp, boolean bold) {
        TextView t = new TextView(this);
        styleText(t, sp, bold);
        return t;
    }

    private void styleText(TextView t, int sp, boolean bold) {
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private LinearLayout.LayoutParams tileLp(int rightGap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = rightGap;
        return lp;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
