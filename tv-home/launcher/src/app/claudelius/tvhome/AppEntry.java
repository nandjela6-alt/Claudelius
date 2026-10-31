package app.claudelius.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One launchable thing: an installed app, or the virtual HDMI entry. */
final class AppEntry {
    /** Pseudo package name used for the HDMI / inputs tile in the dock. */
    static final String HDMI = "@hdmi";

    final String pkg;
    final String label;
    final ComponentName component; // null for HDMI
    final boolean leanback;

    private Drawable banner;
    private Drawable icon;
    private boolean artLoaded;

    AppEntry(String pkg, String label, ComponentName component, boolean leanback) {
        this.pkg = pkg;
        this.label = label;
        this.component = component;
        this.leanback = leanback;
    }

    boolean isHdmi() {
        return HDMI.equals(pkg);
    }

    /** Lazily load the TV banner (16:9) and icon; only done for visible tiles. */
    void loadArt(PackageManager pm) {
        if (artLoaded || component == null) return;
        artLoaded = true;
        try {
            ActivityInfo ai = pm.getActivityInfo(component, 0);
            banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            icon = ai.loadIcon(pm);
        } catch (PackageManager.NameNotFoundException ignored) {
            // App vanished between query and draw; tile shows the label only.
        }
    }

    Drawable banner() { return banner; }
    Drawable icon() { return icon; }

    Intent launchIntent() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        i.setComponent(component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    /**
     * All launchable apps, one entry per package. TV (leanback) entry points win over phone
     * launcher entry points, because they are the ones built for a remote control.
     */
    static List<AppEntry> queryAll(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, AppEntry> byPkg = new LinkedHashMap<>();
        collect(ctx, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, byPkg);
        collect(ctx, pm, Intent.CATEGORY_LAUNCHER, false, byPkg);
        List<AppEntry> list = new ArrayList<>(byPkg.values());
        final Collator c = Collator.getInstance();
        Collections.sort(list, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) { return c.compare(a.label, b.label); }
        });
        return list;
    }

    private static void collect(Context ctx, PackageManager pm, String category, boolean leanback,
                                Map<String, AppEntry> out) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
        for (ResolveInfo ri : ris) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ctx.getPackageName().equals(ai.packageName)) continue;
            if (out.containsKey(ai.packageName)) continue;
            CharSequence l = ri.loadLabel(pm);
            out.put(ai.packageName, new AppEntry(ai.packageName,
                    l == null ? ai.packageName : l.toString(),
                    new ComponentName(ai.packageName, ai.name), leanback));
        }
    }
}
