package app.claudelius.tvhome;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Switching to HDMI goes through the public Android TV input framework: we ask the system for
 * passthrough inputs and fire a VIEW intent at the passthrough channel URI. The manufacturer's
 * TV app (whichever app handles that intent) does the actual switching, so it must stay enabled.
 */
final class Hdmi {
    private Hdmi() {}

    static List<TvInputInfo> inputs(Context ctx) {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (!info.isPassthroughInput()) continue;
            if (info.isHidden(ctx)) continue;
            // Only HDMI ports themselves, not CEC devices hanging off them (those have a parent).
            if (info.getType() == TvInputInfo.TYPE_HDMI && info.getParentId() != null) continue;
            out.add(info);
        }
        return out;
    }

    static String label(Context ctx, TvInputInfo info) {
        CharSequence custom = info.loadCustomLabel(ctx);
        if (custom != null && custom.length() > 0) return custom.toString();
        CharSequence l = info.loadLabel(ctx);
        return l == null ? info.getId() : l.toString();
    }

    static void open(final Context ctx) {
        final List<TvInputInfo> list = inputs(ctx);
        if (list.isEmpty()) {
            Toast.makeText(ctx, Text.t(ctx, "Keine HDMI-Eingänge gefunden", "No HDMI inputs found"),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (list.size() == 1) { switchTo(ctx, list.get(0)); return; }

        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) {
            String n = label(ctx, list.get(i));
            if (tim != null && tim.getInputState(list.get(i).getId())
                    == TvInputManager.INPUT_STATE_DISCONNECTED) {
                n += Text.t(ctx, "  (nicht verbunden)", "  (not connected)");
            }
            names[i] = n;
        }
        new AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Text.t(ctx, "Eingang wählen", "Choose input"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        switchTo(ctx, list.get(which));
                    }
                })
                .show();
    }

    private static void switchTo(Context ctx, TvInputInfo info) {
        Intent i = new Intent(Intent.ACTION_VIEW,
                TvContract.buildChannelUriForPassthroughInput(info.getId()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, Text.t(ctx,
                    "Keine TV-App zum Umschalten gefunden (wurde sie deaktiviert?)",
                    "No TV app can switch inputs (was it disabled?)"), Toast.LENGTH_LONG).show();
        }
    }
}
