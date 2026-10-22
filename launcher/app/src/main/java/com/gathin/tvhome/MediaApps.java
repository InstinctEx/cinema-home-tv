package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** Explicit package targeting keeps Nuvio and Stremio from opening each other's links. */
final class MediaApps {
    static final String[] NUVIO = {"com.nuvio.tv", "com.nuvio.app"};
    static final String[] STREMIO = {"com.stremio.one"};

    static String preferred(Context context) {
        return context.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE)
                .getString("media_app", "nuvio");
    }

    static String name(Context context) {
        return "stremio".equals(preferred(context)) ? "Stremio" : "NUVIO";
    }

    static String installedPackage(Context context) {
        String[] packages = "stremio".equals(preferred(context)) ? STREMIO : NUVIO;
        for (String pkg : packages) {
            if (launchIntent(context, pkg) != null) return pkg;
        }
        return null;
    }

    private static Intent launchIntent(Context context, String pkg) {
        Intent intent = context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : context.getPackageManager().getLaunchIntentForPackage(pkg);
    }

    static void open(Context context, String type, String id) {
        String pkg = installedPackage(context);
        if (pkg == null) {
            Toast.makeText(context, name(context) + " is not installed. Choose an app in Home settings.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (type != null && id != null) {
            String base = "stremio".equals(preferred(context)) ? "stremio:///detail/" : "nuvio://detail/";
            Intent detail = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(base + Uri.encode(type) + "/" + Uri.encode(id)))
                    .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(detail);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                Toast.makeText(context, "This version cannot open title links; opening " + name(context),
                        Toast.LENGTH_SHORT).show();
            }
        }
        Intent fallback = launchIntent(context, pkg);
        if (fallback != null) {
            try {
                context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException ignored) {
                Toast.makeText(context, "Could not open " + name(context), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private MediaApps() {}
}
