package app.claudelius.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Ordered list of dock package names, persisted in SharedPreferences. */
final class DockStore {
    /** Favourites in the order the owner asked for. Missing apps are simply skipped. */
    static final String[] DEFAULT_DOCK = {
            "com.google.android.youtube.tv",      // YouTube for Android TV
            "com.netflix.ninja",                  // Netflix for Android TV
            "com.amazon.amazonvideo.livingroom",  // Prime Video for Android TV
            "com.crunchyroll.crunchyroid",        // Crunchyroll
            "com.spotify.tv.android",             // Spotify for Android TV
            AppEntry.HDMI,                        // HDMI inputs
    };

    private static final String PREFS = "dock";
    private static final String KEY = "packages";

    private final SharedPreferences prefs;

    DockStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<String> load() {
        String raw = prefs.getString(KEY, null);
        if (raw == null) return new ArrayList<>(Arrays.asList(DEFAULT_DOCK));
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\n")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    void save(List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String p : pkgs) sb.append(p).append('\n');
        prefs.edit().putString(KEY, sb.toString()).apply();
    }

    boolean contains(String pkg) {
        return load().contains(pkg);
    }

    void add(String pkg) {
        List<String> l = load();
        if (!l.contains(pkg)) { l.add(pkg); save(l); }
    }

    void remove(String pkg) {
        List<String> l = load();
        if (l.remove(pkg)) save(l);
    }

    /** Move pkg by delta positions (−1 = left, +1 = right). */
    void move(String pkg, int delta) {
        List<String> l = load();
        int i = l.indexOf(pkg);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= l.size()) return;
        l.remove(i);
        l.add(j, pkg);
        save(l);
    }
}
