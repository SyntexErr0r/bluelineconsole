package net.nhiroki.bluelineconsole.agent;

import android.app.SearchManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentActionEngine {
    public static class Action {
        public String type; // "OPEN_APP", "SEARCH", "SEARCH_APP", "CLICK", "TYPE", "OPEN_URL", "PRESS"
        public String appName;
        public String query;
        public String target;

        public Action(String type, String appName, String query) {
            this.type = type;
            this.appName = appName;
            this.query = query;
        }

        public Action(String type, String target) {
            this.type = type;
            this.target = target;
        }
    }

    private static final Map<String, String> KNOWN_APP_PACKAGES = new HashMap<>();
    static {
        KNOWN_APP_PACKAGES.put("youtube", "com.google.android.youtube");
        KNOWN_APP_PACKAGES.put("spotify", "com.spotify.music");
        KNOWN_APP_PACKAGES.put("maps", "com.google.android.apps.maps");
        KNOWN_APP_PACKAGES.put("chrome", "com.android.chrome");
        KNOWN_APP_PACKAGES.put("play store", "com.android.vending");
        KNOWN_APP_PACKAGES.put("playstore", "com.android.vending");
        KNOWN_APP_PACKAGES.put("store", "com.android.vending");
        KNOWN_APP_PACKAGES.put("whatsapp", "com.whatsapp");
        KNOWN_APP_PACKAGES.put("telegram", "org.telegram.messenger");
        KNOWN_APP_PACKAGES.put("instagram", "com.instagram.android");
        KNOWN_APP_PACKAGES.put("twitter", "com.twitter.android");
        KNOWN_APP_PACKAGES.put("x", "com.twitter.android");
        KNOWN_APP_PACKAGES.put("reddit", "com.reddit.frontpage");
        KNOWN_APP_PACKAGES.put("discord", "com.discord");
        KNOWN_APP_PACKAGES.put("netflix", "com.netflix.mediaclient");
        KNOWN_APP_PACKAGES.put("amazon", "com.amazon.mShop.android.shopping");
        KNOWN_APP_PACKAGES.put("github", "com.github.android");
        KNOWN_APP_PACKAGES.put("gmail", "com.google.android.gm");
        KNOWN_APP_PACKAGES.put("mail", "com.google.android.gm");
        KNOWN_APP_PACKAGES.put("photos", "com.google.android.apps.photos");
        KNOWN_APP_PACKAGES.put("gallery", "com.google.android.apps.photos");
        KNOWN_APP_PACKAGES.put("files", "com.google.android.documentsui");
        KNOWN_APP_PACKAGES.put("messages", "com.google.android.apps.messaging");
        KNOWN_APP_PACKAGES.put("sms", "com.google.android.apps.messaging");
        KNOWN_APP_PACKAGES.put("phone", "com.google.android.dialer");
        KNOWN_APP_PACKAGES.put("dialer", "com.google.android.dialer");
        KNOWN_APP_PACKAGES.put("contacts", "com.google.android.contacts");
        KNOWN_APP_PACKAGES.put("settings", "com.android.settings");
    }

    public static void executeAction(final Context context, final Action action) {
        if (action == null || context == null) return;
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        if ("OPEN_APP".equalsIgnoreCase(action.type)) {
            launchAppByName(context, action.appName);

        } else if ("SEARCH".equalsIgnoreCase(action.type) || "SEARCH_APP".equalsIgnoreCase(action.type)) {
            executeSearchApp(context, action, mainHandler);

        } else if ("CLICK".equalsIgnoreCase(action.type)) {
            if (BlueLineAgentService.isServiceConnected()) {
                boolean done = BlueLineAgentService.getInstance().clickByText(action.target, false);
                if (!done) done = BlueLineAgentService.getInstance().clickById(action.target);
                if (!done) {
                    Toast.makeText(context, "Could not find element: " + action.target, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(context, "Accessibility Service is required for clicks. Enable it in Settings.", Toast.LENGTH_LONG).show();
            }

        } else if ("TYPE".equalsIgnoreCase(action.type)) {
            if (BlueLineAgentService.isServiceConnected()) {
                BlueLineAgentService.getInstance().typeText(action.query != null ? action.query : action.target);
            } else {
                Toast.makeText(context, "Accessibility Service is required for typing.", Toast.LENGTH_SHORT).show();
            }

        } else if ("OPEN_URL".equalsIgnoreCase(action.type)) {
            String url = action.query != null ? action.query : action.target;
            if (url != null) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception ignored) {}
            }

        } else if ("PRESS".equalsIgnoreCase(action.type)) {
            if (BlueLineAgentService.isServiceConnected()) {
                if ("BACK".equalsIgnoreCase(action.target)) {
                    BlueLineAgentService.getInstance().pressBack();
                } else if ("HOME".equalsIgnoreCase(action.target)) {
                    BlueLineAgentService.getInstance().pressHome();
                } else if ("RECENTS".equalsIgnoreCase(action.target)) {
                    BlueLineAgentService.getInstance().pressRecents();
                }
            }
        }
    }

    private static void executeSearchApp(final Context context, final Action action, final Handler mainHandler) {
        String app = action.appName != null ? action.appName.trim().toLowerCase() : "";
        String query = action.query != null ? action.query.trim() : "";
        if (query.isEmpty() && action.target != null) {
            query = action.target.trim();
        }

        // 1. YouTube
        if (app.contains("youtube")) {
            try {
                Intent ytIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:results?q=" + Uri.encode(query)));
                ytIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(ytIntent);
                return;
            } catch (Exception e1) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
                    intent.setPackage("com.google.android.youtube");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return;
                } catch (Exception e2) {
                    launchWebUri(context, "https://www.youtube.com/results?search_query=" + Uri.encode(query));
                    return;
                }
            }
        }

        // 2. Spotify
        if (app.contains("spotify")) {
            try {
                Intent spotIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(query)));
                spotIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(spotIntent);
                return;
            } catch (Exception e1) {
                try {
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/" + Uri.encode(query)));
                    webIntent.setPackage("com.spotify.music");
                    webIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(webIntent);
                    return;
                } catch (Exception e2) {
                    launchWebUri(context, "https://open.spotify.com/search/" + Uri.encode(query));
                    return;
                }
            }
        }

        // 3. Google Maps
        if (app.contains("map")) {
            try {
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)));
                mapIntent.setPackage("com.google.android.apps.maps");
                mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(mapIntent);
                return;
            } catch (Exception e1) {
                try {
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)));
                    mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(mapIntent);
                    return;
                } catch (Exception e2) {
                    launchWebUri(context, "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query));
                    return;
                }
            }
        }

        // 4. Play Store / Google Play
        if (app.contains("play") || app.contains("store") || app.contains("market")) {
            try {
                Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query)));
                marketIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(marketIntent);
                return;
            } catch (Exception ignored) {
                launchWebUri(context, "https://play.google.com/store/search?q=" + Uri.encode(query));
                return;
            }
        }

        // 5. Browser / Google / Chrome
        if (app.contains("chrome") || app.contains("browser") || app.contains("google") || app.isEmpty()) {
            launchWebUri(context, "https://www.google.com/search?q=" + Uri.encode(query));
            return;
        }

        // 6. Twitter / X
        if (app.contains("twitter") || app.equals("x")) {
            try {
                Intent twIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("twitter://search?query=" + Uri.encode(query)));
                twIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(twIntent);
                return;
            } catch (Exception ignored) {
                launchWebUri(context, "https://twitter.com/search?q=" + Uri.encode(query));
                return;
            }
        }

        // 7. Reddit
        if (app.contains("reddit")) {
            try {
                Intent redIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/search/?q=" + Uri.encode(query)));
                redIntent.setPackage("com.reddit.frontpage");
                redIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(redIntent);
                return;
            } catch (Exception ignored) {
                launchWebUri(context, "https://www.reddit.com/search/?q=" + Uri.encode(query));
                return;
            }
        }

        // 8. Amazon
        if (app.contains("amazon")) {
            launchWebUri(context, "https://www.amazon.com/s?k=" + Uri.encode(query));
            return;
        }

        // 9. Wikipedia
        if (app.contains("wiki")) {
            launchWebUri(context, "https://en.wikipedia.org/wiki/Special:Search?search=" + Uri.encode(query));
            return;
        }

        // 10. GitHub
        if (app.contains("github")) {
            launchWebUri(context, "https://github.com/search?q=" + Uri.encode(query));
            return;
        }

        // 11. Generic App Search: Try standard ACTION_SEARCH with target package
        String pkg = findPackageByName(context, action.appName);
        if (pkg != null) {
            Intent searchIntent = new Intent(Intent.ACTION_SEARCH);
            searchIntent.setPackage(pkg);
            searchIntent.putExtra(SearchManager.QUERY, query);
            searchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (context.getPackageManager().queryIntentActivities(searchIntent, 0).size() > 0) {
                    context.startActivity(searchIntent);
                    return;
                }
            } catch (Exception ignored) {}

            // Launch app directly and use accessibility service if available
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launch);

                if (BlueLineAgentService.isServiceConnected()) {
                    final String searchQuery = query;
                    mainHandler.postDelayed(() -> {
                        BlueLineAgentService service = BlueLineAgentService.getInstance();
                        if (service != null) {
                            service.performInAppSearch(searchQuery);
                        }
                    }, 500);

                    mainHandler.postDelayed(() -> {
                        BlueLineAgentService service = BlueLineAgentService.getInstance();
                        if (service != null) {
                            service.performInAppSearch(searchQuery);
                        }
                    }, 1200);
                }
                return;
            }
        }

        // Fallback: Google search
        launchWebUri(context, "https://www.google.com/search?q=" + Uri.encode(query));
    }

    private static void launchWebUri(Context context, String url) {
        try {
            Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            webIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(webIntent);
        } catch (Exception ignored) {}
    }

    public static String findPackageByName(Context context, String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String query = name.trim().toLowerCase();
        PackageManager pm = context.getPackageManager();

        // 1. Check known aliases
        String known = KNOWN_APP_PACKAGES.get(query);
        if (known != null) {
            try {
                pm.getPackageInfo(known, 0);
                return known;
            } catch (Exception ignored) {}
        }

        // 2. Query all launchable activities via PackageManager
        try {
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);
            if (activities != null) {
                // Pass 1: exact match
                for (ResolveInfo ri : activities) {
                    if (ri.activityInfo == null) continue;
                    CharSequence label = ri.loadLabel(pm);
                    if (label != null && label.toString().equalsIgnoreCase(name)) {
                        return ri.activityInfo.packageName;
                    }
                }
                // Pass 2: label starts with query or contains query
                for (ResolveInfo ri : activities) {
                    if (ri.activityInfo == null) continue;
                    CharSequence label = ri.loadLabel(pm);
                    if (label != null && label.toString().toLowerCase().contains(query)) {
                        return ri.activityInfo.packageName;
                    }
                }
                // Pass 3: package name contains query
                for (ResolveInfo ri : activities) {
                    if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                        if (ri.activityInfo.packageName.toLowerCase().contains(query)) {
                            return ri.activityInfo.packageName;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static void launchAppByName(Context context, String name) {
        if (name == null || name.trim().isEmpty()) return;
        String query = name.trim().toLowerCase();

        // Special system intents
        if (query.equals("settings")) {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception ignored) {}
        }
        if (query.equals("camera")) {
            try {
                Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception ignored) {}
        }
        if (query.equals("calculator")) {
            String[] calcPackages = {"com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator"};
            for (String p : calcPackages) {
                try {
                    Intent launch = context.getPackageManager().getLaunchIntentForPackage(p);
                    if (launch != null) {
                        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(launch);
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }
        if (query.equals("clock") || query.equals("alarm")) {
            try {
                Intent intent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception ignored) {}
        }
        if (query.equals("calendar")) {
            try {
                Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
                builder.appendPath("time");
                ContentUris.appendId(builder, System.currentTimeMillis());
                Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception ignored) {}
        }

        String pkg = findPackageByName(context, name);
        if (pkg != null) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launch);
                return;
            }
        }
        Toast.makeText(context, "Could not find app: " + name, Toast.LENGTH_SHORT).show();
    }
}
