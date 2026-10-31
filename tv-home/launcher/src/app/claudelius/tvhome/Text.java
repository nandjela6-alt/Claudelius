package app.claudelius.tvhome;

import android.content.Context;

/** Tiny German/English switch; avoids a resource table for a handful of strings. */
final class Text {
    private Text() {}

    static String t(Context ctx, String de, String en) {
        String lang = ctx.getResources().getConfiguration().getLocales().get(0).getLanguage();
        return "de".equals(lang) ? de : en;
    }
}
