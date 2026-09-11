package net.nhiroki.bluelineconsole.agent;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import net.nhiroki.bluelineconsole.commands.applications.ApplicationDatabase;
import net.nhiroki.bluelineconsole.dataStore.cache.ApplicationInformation;

public class AgentActionEngine {
    public static class Action {
        public String type; // "OPEN_APP", "SEARCH", "CLICK", "TYPE", "OPEN_URL", "PRESS"
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

    public static void executeAction(final Context context, final Action action) {
        if (action == null || context == null) return;
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        if ("OPEN_APP".equalsIgnoreCase(action.type)) {
            launchAppByName(context, action.appName);
            AgentTTS.speak(context, "Opening " + action.appName);

        } else if ("SEARCH".equalsIgnoreCase(action.type) || "SEARCH_APP".equalsIgnoreCase(action.type)) {
            String app = action.appName != null ? action.appName.toLowerCase() : "";
            AgentTTS.speak(context, "Searching " + action.query + (app.isEmpty() ? "" : " on " + action.appName));

            // YouTube special intent (fast, direct)
            if (app.contains("youtube")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(action.query)));
                    intent.setPackage("com.google.android.youtube");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return;
                } catch (Exception ignored) {
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(action.query)));
                    webIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(webIntent);
                    return;
                }
            }

            // Maps special intent
            if (app.contains("map")) {
                try {
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(action.query)));
                    mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(mapIntent);
                    return;
                } catch (Exception ignored) {}
            }

            // Browser special intent
            if (app.contains("chrome") || app.contains("browser")) {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(action.query)));
                webIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(webIntent);
                return;
            }

            // Other apps: Launch the app, then let Accessibility agent perform the search!
            String pkg = findPackageByName(context, action.appName);
            if (pkg != null) {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);

                    if (BlueLineAgentService.isServiceConnected()) {
                        mainHandler.postDelayed(() -> {
                            BlueLineAgentService service = BlueLineAgentService.getInstance();
                            if (service != null) {
                                boolean clicked = service.clickByText("Search", false);
                                if (!clicked) clicked = service.clickById("search_button");
                                if (!clicked) clicked = service.clickById("menu_search");
                                if (!clicked) clicked = service.clickById("search_bar");

                                mainHandler.postDelayed(() -> {
                                    service.typeText(action.query);
                                    mainHandler.postDelayed(() -> {
                                        service.clickByText("Search", true);
                                    }, 400);
                                }, 600);
                            }
                        }, 1200);
                    }
                    return;
                }
            }

            // Generic web search fallback
            Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
            searchIntent.putExtra(SearchManager.QUERY, action.query);
            searchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(searchIntent);

        } else if ("CLICK".equalsIgnoreCase(action.type)) {
            if (BlueLineAgentService.isServiceConnected()) {
                boolean done = BlueLineAgentService.getInstance().clickByText(action.target, false);
                if (!done) done = BlueLineAgentService.getInstance().clickById(action.target);
                if (done) AgentTTS.speak(context, "Clicked " + action.target);
            } else {
                Toast.makeText(context, "Accessibility Agent not enabled", Toast.LENGTH_SHORT).show();
            }

        } else if ("TYPE".equalsIgnoreCase(action.type)) {
            if (BlueLineAgentService.isServiceConnected()) {
                BlueLineAgentService.getInstance().typeText(action.query);
            }

        } else if ("OPEN_URL".equalsIgnoreCase(action.type)) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(action.query));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ignored) {}

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

    public static String findPackageByName(Context context, String name) {
        if (name == null || name.trim().isEmpty()) return null;
        try {
            ApplicationDatabase db = new ApplicationDatabase(context);
            db.waitUntilPrepared();
            for (ApplicationInformation app : db.getApplicationInformationList()) {
                if (app.getLabel().equalsIgnoreCase(name) || app.getLabel().toLowerCase().contains(name.toLowerCase())) {
                    return app.getPackageName();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void launchAppByName(Context context, String name) {
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
