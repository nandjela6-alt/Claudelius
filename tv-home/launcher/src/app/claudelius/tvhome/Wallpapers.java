package app.claudelius.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cross-fades between photos in {@code /sdcard/Android/data/app.claudelius.tvhome/files/wallpapers}.
 * Decoding happens on a background thread, downsampled to screen size, and at most two bitmaps
 * are alive at once. No animation runs between fades, so the GPU is idle while you browse.
 */
final class Wallpapers {
    static final long INTERVAL_MS = 3 * 60 * 1000L; // new photo every 3 minutes
    private static final long FADE_MS = 1500L;

    private final Context ctx;
    private final ImageView front, back;
    private final TextView credit;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final HandlerThread worker = new HandlerThread("wallpaper-decode");
    private final Handler bg;

    private final List<File> files = new ArrayList<>();
    private final Map<String, String> credits = new HashMap<>();
    private int index = -1;
    private boolean running;
    private ImageView shown;
    private Bitmap shownBitmap;
    private final int targetW, targetH;

    private final Runnable tick = new Runnable() {
        @Override public void run() { next(); }
    };

    Wallpapers(Context ctx, ImageView a, ImageView b, TextView credit) {
        this.ctx = ctx;
        this.front = a;
        this.back = b;
        this.credit = credit;
        worker.start();
        bg = new Handler(worker.getLooper());
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        targetW = Math.max(dm.widthPixels, dm.heightPixels);
        targetH = Math.min(dm.widthPixels, dm.heightPixels);
    }

    static File dir(Context ctx) {
        File d = ctx.getExternalFilesDir("wallpapers");
        if (d != null && !d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    void start() {
        if (running) return;
        running = true;
        rescan();
        if (shown == null) next(); else ui.postDelayed(tick, INTERVAL_MS);
    }

    void stop() {
        running = false;
        ui.removeCallbacks(tick);
    }

    void destroy() {
        stop();
        worker.quitSafely();
    }

    private void rescan() {
        files.clear();
        credits.clear();
        File d = dir(ctx);
        if (d == null) return;
        File[] list = d.listFiles();
        if (list == null) return;
        for (File f : list) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) {
                files.add(f);
            }
        }
        Collections.shuffle(files);
        readCredits(new File(d, "credits.tsv"));
    }

    /** credits.tsv: one line per photo, "filename<TAB>caption". Written by fetch-wallpapers.py. */
    private void readCredits(File f) {
        if (!f.isFile()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab > 0) credits.put(line.substring(0, tab), line.substring(tab + 1));
            }
        } catch (IOException ignored) {
            // Captions are optional.
        }
    }

    private void next() {
        ui.removeCallbacks(tick);
        if (!running) return;
        if (files.isEmpty()) { // nothing pushed yet: keep the gradient fallback, check again later
            rescan();
            if (files.isEmpty()) { ui.postDelayed(tick, INTERVAL_MS); return; }
        }
        index = (index + 1) % files.size();
        final File f = files.get(index);
        bg.post(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = decode(f);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (bmp != null) show(bmp, credits.get(f.getName()));
                        if (running) ui.postDelayed(tick, bmp == null ? 2000L : INTERVAL_MS);
                    }
                });
            }
        });
    }

    private Bitmap decode(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= targetW && o.outHeight / (sample * 2) >= targetH) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try {
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private void show(Bitmap bmp, String caption) {
        final ImageView incoming = (shown == front) ? back : front;
        final ImageView outgoing = shown;
        final Bitmap old = shownBitmap;
        incoming.setImageBitmap(bmp);
        incoming.setAlpha(0f);
        incoming.bringToFront();
        incoming.animate().alpha(1f).setDuration(FADE_MS).withEndAction(new Runnable() {
            @Override public void run() {
                if (outgoing != null) outgoing.setImageDrawable(null);
                if (old != null && !old.isRecycled()) old.recycle();
            }
        }).start();
        shown = incoming;
        shownBitmap = bmp;
        credit.animate().alpha(0f).setDuration(FADE_MS / 2).withEndAction(new Runnable() {
            @Override public void run() {
                credit.setText(caption == null ? "" : caption);
                credit.animate().alpha(caption == null ? 0f : 0.75f).setDuration(FADE_MS / 2).start();
            }
        }).start();
    }
}
