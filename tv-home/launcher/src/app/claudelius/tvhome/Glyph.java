package app.claudelius.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Minimal vector glyphs drawn in code, so we don't depend on symbol fonts the TV may lack. */
final class Glyph extends Drawable {
    static final int APPS = 0, SETTINGS = 1;

    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Glyph(int kind) {
        this.kind = kind;
        p.setColor(0xFFFFFFFF);
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) * 0.42f;
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        if (kind == APPS) {
            float r = s * 0.13f, step = s * 0.4f;
            for (int y = -1; y <= 1; y++)
                for (int x = -1; x <= 1; x++)
                    c.drawCircle(cx + x * step, cy + y * step, r, p);
        } else {
            // Gear: 8 rectangular teeth around a ring.
            Path path = new Path();
            float outer = s * 0.55f, inner = s * 0.40f, hole = s * 0.17f;
            int teeth = 8;
            for (int i = 0; i < teeth * 2; i++) {
                double a0 = Math.PI * 2 * i / (teeth * 2), a1 = Math.PI * 2 * (i + 1) / (teeth * 2);
                float r = (i % 2 == 0) ? outer : inner;
                if (i == 0) path.moveTo(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r);
                else path.lineTo(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r);
                path.lineTo(cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r);
            }
            path.close();
            path.addCircle(cx, cy, hole, Path.Direction.CCW);
            path.setFillType(Path.FillType.EVEN_ODD);
            c.drawPath(path, p);
        }
    }

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
