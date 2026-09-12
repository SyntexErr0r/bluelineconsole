package net.nhiroki.bluelineconsole.agent;

import android.Manifest;
import android.app.SearchManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.telecom.TelecomManager;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import net.nhiroki.bluelineconsole.commands.logs.AppLogger;
import net.nhiroki.bluelineconsole.contacts.ContactManager;
import net.nhiroki.bluelineconsole.wrapperForAndroid.ContactsReader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentActionEngine {
    public static class Action {
        public String type; // "OPEN_APP", "SEARCH", "SEARCH_APP", "CLICK", "TYPE", "OPEN_URL", "PRESS", "SEND_MESSAGE"
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

        public Action(String type, String appName, String target, String query) {
            this.type = type;
            this.appName = appName;
            this.target = target;
            this.query = query;
        }
    }

    public static class MessageDetails {
        public final String recipient;
        public final String message;

        public MessageDetails(String recipient, String message) {
            this.recipient = recipient != null ? recipient.trim() : "";
            this.message = message != null ? message.trim() : "";
        }
    }

    public static MessageDetails parseMessageDetails(String text) {
        if (text == null) return new MessageDetails("", "");
        String trimmed = text.trim();
        String low = trimmed.toLowerCase();

        // 1. "send <msg> to <recipient>"
        if (low.startsWith("send ") && low.contains(" to ")) {
            int toIdx = low.lastIndexOf(" to ");
            String msg = trimmed.substring(5, toIdx).trim();
            String recip = trimmed.substring(toIdx + 4).trim();
            return new MessageDetails(recip, msg);
        }

        // 2. "<recipient> send <msg>"
        if (low.contains(" send ")) {
            int sIdx = low.indexOf(" send ");
            String recip = trimmed.substring(0, sIdx).trim();
            String msg = trimmed.substring(sIdx + 6).trim();
            return new MessageDetails(recip, msg);
        }

        // 3. "<recipient> sent <msg>"
        if (low.contains(" sent ")) {
            int sIdx = low.indexOf(" sent ");
            String recip = trimmed.substring(0, sIdx).trim();
            String msg = trimmed.substring(sIdx + 6).trim();
            return new MessageDetails(recip, msg);
        }

        // 4. "<recipient> : <msg>"
        if (trimmed.contains(":")) {
            int cIdx = trimmed.indexOf(':');
            String recip = trimmed.substring(0, cIdx).trim();
            String msg = trimmed.substring(cIdx + 1).trim();
            return new MessageDetails(recip, msg);
        }

        // 5. "send <msg>" (no recipient specified)
        if (low.startsWith("send ")) {
            String msg = trimmed.substring(5).trim();
            return new MessageDetails("", msg);
        }

        // 6. "to <recipient> <msg>"
        if (low.startsWith("to ")) {
            int space = trimmed.indexOf(' ', 3);
            if (space > 3) {
                String recip = trimmed.substring(3, space).trim();
                String msg = trimmed.substring(space + 1).trim();
                return new MessageDetails(recip, msg);
            } else {
                String recip = trimmed.substring(3).trim();
                return new MessageDetails(recip, "");
            }
        }

        return new MessageDetails(trimmed, "");
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
        AppLogger.i("ACTION", "executeAction called: type=" + action.type + ", app=" + action.appName + ", query=" + action.query + ", target=" + action.target);
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        if ("OPEN_APP".equalsIgnoreCase(action.type)) {
            if ("whatsapp_group".equalsIgnoreCase(action.appName)) {
                executeWhatsAppGroupOpen(context, action.target != null ? action.target : action.query);
            } else {
                launchAppByName(context, action.appName);
            }

        } else if ("SEARCH".equalsIgnoreCase(action.type) || "SEARCH_APP".equalsIgnoreCase(action.type)) {
            executeSearchApp(context, action, mainHandler);

        } else if ("SEND_MESSAGE".equalsIgnoreCase(action.type)) {
            executeSendMessage(context, action);

        } else if ("CALL_APP".equalsIgnoreCase(action.type)) {
            executeCallApp(context, action);

        } else if ("CLICK".equalsIgnoreCase(action.type)) {
            if (BlueLineAgentService.isServiceConnected()) {
                AppLogger.i("ACTION", "Performing CLICK on: " + action.target);
                boolean done = BlueLineAgentService.getInstance().clickByText(action.target, false);
                if (!done) done = BlueLineAgentService.getInstance().clickById(action.target);
                if (!done) {
                    AppLogger.w("ACTION", "CLICK failed: element not found: " + action.target);
                    Toast.makeText(context, "Could not find element: " + action.target, Toast.LENGTH_SHORT).show();
                } else {
                    AppLogger.i("ACTION", "CLICK successful on: " + action.target);
                }
            } else {
                AppLogger.w("ACTION", "CLICK skipped: Accessibility Service not connected");
                Toast.makeText(context, "Accessibility Service is required for clicks. Enable it in Settings.", Toast.LENGTH_LONG).show();
            }

        } else if ("TYPE".equalsIgnoreCase(action.type)) {
            if (BlueLineAgentService.isServiceConnected()) {
                String text = action.query != null ? action.query : action.target;
                AppLogger.i("ACTION", "Performing TYPE: '" + text + "'");
                boolean done = BlueLineAgentService.getInstance().typeText(text);
                AppLogger.i("ACTION", "TYPE result: " + (done ? "SUCCESS" : "FAILED"));
            } else {
                AppLogger.w("ACTION", "TYPE skipped: Accessibility Service not connected");
                Toast.makeText(context, "Accessibility Service is required for typing.", Toast.LENGTH_SHORT).show();
            }

        } else if ("OPEN_URL".equalsIgnoreCase(action.type)) {
            String url = action.query != null ? action.query : action.target;
            if (url != null) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                AppLogger.i("ACTION", "Opening URL: " + url);
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    AppLogger.e("ACTION", "Failed to open URL: " + url, e);
                }
            }

        } else if ("PRESS".equalsIgnoreCase(action.type)) {
            if (BlueLineAgentService.isServiceConnected()) {
                AppLogger.i("ACTION", "Performing PRESS: " + action.target);
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

        // Messaging apps
        if (app.contains("whatsapp") || app.equals("wa")) {
            String qLow = query.toLowerCase();
            if (qLow.startsWith("call ") || qLow.startsWith("video call ")) {
                boolean isVideo = qLow.startsWith("video call ");
                String contact = isVideo ? query.substring(11).trim() : query.substring(5).trim();
                executeWhatsAppCall(context, contact, isVideo);
                return;
            }
            MessageDetails details = parseMessageDetails(query);
            String recipient = action.target != null && !action.target.isEmpty() ? action.target : details.recipient;
            String msg = !details.message.isEmpty() ? details.message : "";
            if (recipient.isEmpty() && msg.isEmpty()) {
                recipient = query;
            }
            executeWhatsApp(context, recipient, msg);
            return;
        }

        if (app.contains("telegram") || app.equals("tg")) {
            MessageDetails details = parseMessageDetails(query);
            String recipient = action.target != null && !action.target.isEmpty() ? action.target : details.recipient;
            String msg = !details.message.isEmpty() ? details.message : "";
            if (recipient.isEmpty() && msg.isEmpty()) {
                recipient = query;
            }
            executeTelegram(context, recipient, msg);
            return;
        }

        if (app.contains("sms") || app.equals("messages")) {
            MessageDetails details = parseMessageDetails(query);
            String recipient = action.target != null && !action.target.isEmpty() ? action.target : details.recipient;
            String msg = !details.message.isEmpty() ? details.message : "";
            executeSms(context, recipient, msg);
            return;
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

            // Launch app directly
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                if (!(context instanceof android.app.Activity)) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(launch);
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

        // Pass 4: If compound string (e.g. "whatsapp find NAME"), try first word
        if (query.contains(" ")) {
            String firstWord = query.split("\\s+")[0];
            if (!firstWord.isEmpty() && !firstWord.equals(query)) {
                String fallbackPkg = findPackageByName(context, firstWord);
                if (fallbackPkg != null) {
                    AppLogger.i("ACTION", "findPackageByName: compound query '" + name + "' matched first token '" + firstWord + "' -> " + fallbackPkg);
                    return fallbackPkg;
                }
            }
        }

        return null;
    }

    public static void launchAppByName(Context context, String name) {
        if (name == null || name.trim().isEmpty()) return;
        AppLogger.i("ACTION", "launchAppByName: '" + name + "'");
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
                        if (!(context instanceof android.app.Activity)) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
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
            AppLogger.i("ACTION", "launchAppByName: launching package '" + pkg + "'");
            net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole(pkg);
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                if (!(context instanceof android.app.Activity)) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(launch);
                return;
            }
        }
        AppLogger.w("ACTION", "launchAppByName: Could NOT find app for '" + name + "'");
        Toast.makeText(context, "Could not find app: " + name, Toast.LENGTH_SHORT).show();
    }

    public static void executeSendMessage(final Context context, final Action action) {
        String app = action.appName != null ? action.appName.trim().toLowerCase() : "whatsapp";
        String recipient = action.target != null ? action.target.trim() : "";
        String message = action.query != null ? action.query.trim() : "";

        if (recipient.isEmpty() && !message.isEmpty()) {
            MessageDetails details = parseMessageDetails(message);
            if (!details.recipient.isEmpty()) {
                recipient = details.recipient;
                message = details.message;
            }
        }

        if (app.contains("whatsapp") || app.equals("wa")) {
            if ("whatsapp_group".equalsIgnoreCase(app) && recipient != null && !recipient.toLowerCase().startsWith("group ")) {
                recipient = "group " + recipient;
            }
            executeWhatsApp(context, recipient, message);
        } else if (app.contains("telegram") || app.equals("tg")) {
            executeTelegram(context, recipient, message);
        } else if (app.contains("sms") || app.contains("message") || app.contains("msg")) {
            executeSms(context, recipient, message);
        } else {
            executeWhatsApp(context, recipient, message);
        }
    }

    public static void executeWhatsApp(Context context, String recipient, String message) {
        AppLogger.i("ACTION", "executeWhatsApp: recipient='" + recipient + "', message='" + message + "'");
        boolean isExplicitGroup = false;
        if (recipient != null && recipient.toLowerCase().startsWith("group ")) {
            isExplicitGroup = true;
            recipient = recipient.substring(6).trim();
        }

        if (isExplicitGroup && (message == null || message.trim().isEmpty())) {
            executeWhatsAppGroupOpen(context, recipient);
            return;
        }

        String phone = null;
        if (!isExplicitGroup && recipient != null && !recipient.isEmpty()) {
            String cleanNum = recipient.replaceAll("[^0-9+]", "");
            if (cleanNum.length() >= 7) {
                phone = cleanNum.replaceAll("[^0-9]", "");
            } else {
                phone = findPhoneNumberForContact(context, recipient);
                if (phone == null && !ContactsReader.appHasReadContactsPermission(context)) {
                    Toast.makeText(context, "Tip: Enable Contacts permission in config for automatic phone lookup", Toast.LENGTH_SHORT).show();
                }
            }
        }

        // If recipient was empty but message has multiple words, see if first word matches a known contact
        if (!isExplicitGroup && (phone == null || phone.isEmpty()) && (recipient == null || recipient.isEmpty()) && message != null && message.contains(" ")) {
            String firstWord = message.split("\\s+")[0];
            String resolved = findPhoneNumberForContact(context, firstWord);
            if (resolved != null) {
                phone = resolved;
                message = message.substring(firstWord.length()).trim();
                AppLogger.i("ACTION", "executeWhatsApp: auto-extracted recipient '" + firstWord + "' from message start");
            }
        }

        Uri uri;
        final boolean isDirectPhone = (phone != null && !phone.isEmpty());
        if (isDirectPhone) {
            if (message != null && !message.isEmpty()) {
                uri = Uri.parse("https://api.whatsapp.com/send?phone=" + Uri.encode(phone) + "&text=" + Uri.encode(message));
            } else {
                uri = Uri.parse("https://api.whatsapp.com/send?phone=" + Uri.encode(phone));
            }
        } else {
            if (message != null && !message.isEmpty()) {
                uri = Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(message));
            } else {
                if (recipient != null && !recipient.isEmpty()) {
                    executeWhatsAppGroupOpen(context, recipient);
                } else {
                    launchAppByName(context, "whatsapp");
                }
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.whatsapp");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            if (context.getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
                net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp");
                context.startActivity(intent);
                triggerWhatsAppAutoSendIfConfigured(context, recipient, message, isDirectPhone);
                return;
            }
        } catch (Exception ignored) {}

        // Try WhatsApp Business
        try {
            intent.setPackage("com.whatsapp.w4b");
            if (context.getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
                net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp.w4b");
                context.startActivity(intent);
                triggerWhatsAppAutoSendIfConfigured(context, recipient, message, isDirectPhone);
                return;
            }
        } catch (Exception ignored) {}

        // Fallback: general chooser
        try {
            Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, uri);
            fallbackIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallbackIntent);
            triggerWhatsAppAutoSendIfConfigured(context, recipient, message, isDirectPhone);
        } catch (Exception e) {
            AppLogger.e("ACTION", "Failed to launch WhatsApp intent: " + uri, e);
            Toast.makeText(context, "WhatsApp is not installed.", Toast.LENGTH_SHORT).show();
        }
    }

    public static void executeTelegram(Context context, String recipient, String message) {
        AppLogger.i("ACTION", "executeTelegram: recipient='" + recipient + "', message='" + message + "'");
        Uri uri = null;
        if (recipient != null && recipient.startsWith("@")) {
            uri = Uri.parse("https://t.me/" + recipient.substring(1));
        } else if (message != null && !message.isEmpty()) {
            uri = Uri.parse("https://t.me/share/url?url=&text=" + Uri.encode(message));
        }

        if (uri != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("org.telegram.messenger");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (context.getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
                    net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole("org.telegram.messenger");
                    context.startActivity(intent);
                    return;
                }
            } catch (Exception ignored) {}
        }
        launchAppByName(context, "telegram");
    }

    public static void executeSms(Context context, String recipient, String message) {
        AppLogger.i("ACTION", "executeSms: recipient='" + recipient + "', message='" + message + "'");
        String phone = null;
        if (recipient != null && !recipient.isEmpty()) {
            String cleanNum = recipient.replaceAll("[^0-9+]", "");
            if (cleanNum.length() >= 7) {
                phone = cleanNum;
            } else {
                phone = findPhoneNumberForContact(context, recipient);
            }
        }
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + (phone != null ? phone : "")));
        if (message != null && !message.isEmpty()) {
            intent.putExtra("sms_body", message);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            AppLogger.e("ACTION", "Failed to launch SMS intent", e);
        }
    }

    public static String findPhoneNumberForContact(Context context, String nameQuery) {
        if (context == null || nameQuery == null || nameQuery.trim().isEmpty()) return null;
        if (!ContactsReader.appHasReadContactsPermission(context)) {
            AppLogger.w("ACTION", "Contacts permission not granted.");
            return null;
        }

        try {
            List<ContactsReader.Contact> contacts = ContactsReader.fetchAllContacts(context);
            if (contacts == null || contacts.isEmpty()) return null;

            String q = nameQuery.trim().toLowerCase();

            // Pass 1: exact match
            for (ContactsReader.Contact c : contacts) {
                if (c.displayName != null && c.displayName.trim().equalsIgnoreCase(q)) {
                    if (c.phoneNumbers != null && !c.phoneNumbers.isEmpty()) {
                        return sanitizePhoneNumber(c.phoneNumbers.get(0));
                    }
                }
            }

            // Pass 2: contains match
            for (ContactsReader.Contact c : contacts) {
                if (c.displayName != null && c.displayName.toLowerCase().contains(q)) {
                    if (c.phoneNumbers != null && !c.phoneNumbers.isEmpty()) {
                        return sanitizePhoneNumber(c.phoneNumbers.get(0));
                    }
                }
            }

            // Pass 3: phonetic match
            for (ContactsReader.Contact c : contacts) {
                if (c.phoneticName != null && c.phoneticName.toLowerCase().contains(q)) {
                    if (c.phoneNumbers != null && !c.phoneNumbers.isEmpty()) {
                        return sanitizePhoneNumber(c.phoneNumbers.get(0));
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.e("ACTION", "Error searching contacts for: " + nameQuery, e);
        }
        return null;
    }

    private static String sanitizePhoneNumber(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("[^0-9]", "");
    }

    public static void executeWhatsAppGroupOpen(Context context, String groupName) {
        AppLogger.i("ACTION", "executeWhatsAppGroupOpen: groupName='" + groupName + "'");
        if (groupName == null || groupName.trim().isEmpty()) {
            launchAppByName(context, "whatsapp");
            return;
        }

        launchAppByName(context, "whatsapp");
        if (BlueLineAgentService.isServiceConnected()) {
            BlueLineAgentService.getInstance().scheduleWhatsAppGroupOpen(groupName);
        } else {
            Toast.makeText(context, "WhatsApp opened. Tap '" + groupName + "' to view group.", Toast.LENGTH_SHORT).show();
        }
    }

    private static void triggerWhatsAppAutoSendIfConfigured(Context context, String message) {
        triggerWhatsAppAutoSendIfConfigured(context, null, message, true);
    }

    private static void triggerWhatsAppAutoSendIfConfigured(Context context, String recipient, String message, boolean isDirectPhone) {
        if (message == null || message.trim().isEmpty()) return;
        boolean autoSend = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("pref_whatsapp_auto_send", true);
        if (autoSend) {
            if (BlueLineAgentService.isServiceConnected()) {
                if (isDirectPhone) {
                    BlueLineAgentService.getInstance().scheduleWhatsAppAutoSend();
                } else if (recipient != null && !recipient.trim().isEmpty()) {
                    BlueLineAgentService.getInstance().scheduleWhatsAppGroupSend(recipient);
                } else {
                    BlueLineAgentService.getInstance().scheduleWhatsAppAutoSend();
                }
            } else {
                if (!isDirectPhone && recipient != null && !recipient.trim().isEmpty()) {
                    Toast.makeText(context, "Select '" + recipient + "' to send (or enable Accessibility for auto-send).", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "Draft opened. Enable Accessibility in Settings for automatic sending.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public static void executeCallApp(final Context context, final Action action) {
        String app = action.appName != null ? action.appName.trim().toLowerCase() : "whatsapp";
        String contact = action.target != null ? action.target.trim() : (action.query != null ? action.query.trim() : "");
        boolean isVideo = "video".equalsIgnoreCase(action.query) || (action.appName != null && action.appName.toLowerCase().contains("video"));

        if (app.contains("telegram") || app.equals("tg")) {
            net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole("org.telegram.messenger");
            executeTelegramCall(context, contact);
        } else if (app.contains("phone") || app.equals("dialer")) {
            executePhoneDial(context, contact);
        } else {
            net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp");
            net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp.w4b");
            executeWhatsAppCall(context, contact, isVideo);
        }
    }

    public static void executeTelegramCall(Context context, String contact) {
        if (contact == null || contact.trim().isEmpty()) {
            launchAppByName(context, "telegram");
            return;
        }
        String cleanUser = contact.trim();
        if (cleanUser.startsWith("@")) cleanUser = cleanUser.substring(1);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + cleanUser));
            intent.setPackage("org.telegram.messenger");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (context.getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
                net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole("org.telegram.messenger");
                context.startActivity(intent);
                return;
            }
        } catch (Exception ignored) {}
        executeTelegram(context, contact, "");
    }

    public static void executeWhatsAppCall(Context context, String contact, boolean isVideo) {
        executeWhatsAppCall(context, contact, null, isVideo);
    }

    public static void executeWhatsAppCall(Context context, String contact, String explicitPhone, boolean isVideo) {
        AppLogger.i("ACTION", "executeWhatsAppCall: contact='" + contact + "', explicitPhone='" + explicitPhone + "', isVideo=" + isVideo);
        if (contact == null || contact.trim().isEmpty()) {
            launchAppByName(context, "whatsapp");
            return;
        }

        // 1. Resolve phone number if possible
        String phone = (explicitPhone != null && !explicitPhone.trim().isEmpty()) ? explicitPhone.replaceAll("[^0-9]", "") : null;
        if (phone == null || phone.isEmpty()) {
            String cleanNum = contact.replaceAll("[^0-9+]", "");
            if (cleanNum.length() >= 7) {
                phone = cleanNum.replaceAll("[^0-9]", "");
            } else {
                phone = findPhoneNumberForContact(context, contact);
            }
        }

        // 2. Try direct WhatsApp VoIP Call Intent via ContactsContract.Data (using contact name & phone digits)
        boolean launchedDirect = launchWhatsAppDirectCallIntent(context, contact, phone, isVideo);
        if (launchedDirect) {
            AppLogger.i("ACTION", "executeWhatsAppCall: launched directly via WhatsApp VoIP data URI");
            return;
        }

        // 3. Fallback: Open chat + schedule call click
        if (phone != null && !phone.isEmpty()) {
            Uri uri = Uri.parse("https://api.whatsapp.com/send?phone=" + Uri.encode(phone));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.whatsapp");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            boolean started = false;
            try {
                net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp");
                context.startActivity(intent);
                started = true;
            } catch (Exception ignored) {
                try {
                    intent.setPackage("com.whatsapp.w4b");
                    net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp.w4b");
                    context.startActivity(intent);
                    started = true;
                } catch (Exception ignored2) {}
            }

            if (started) {
                if (BlueLineAgentService.isServiceConnected()) {
                    BlueLineAgentService.getInstance().scheduleWhatsAppCallClick(isVideo);
                } else {
                    Toast.makeText(context, "Opening WhatsApp. (Enable BlueLine Console in Accessibility Settings to auto-call)", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }

        // 4. Fallback: Launch WhatsApp and notify
        launchAppByName(context, "whatsapp");
        Toast.makeText(context, "Could not find contact '" + contact + "' for WhatsApp call.", Toast.LENGTH_SHORT).show();
    }

    public static boolean launchWhatsAppDirectCallIntent(Context context, String contactName, boolean isVideo) {
        return launchWhatsAppDirectCallIntent(context, contactName, null, isVideo);
    }

    public static boolean launchWhatsAppDirectCallIntent(Context context, String contactName, String phoneNumber, boolean isVideo) {
        if (context == null) return false;
        if ((contactName == null || contactName.trim().isEmpty()) && (phoneNumber == null || phoneNumber.trim().isEmpty())) return false;
        if (!ContactsReader.appHasReadContactsPermission(context)) {
            AppLogger.w("ACTION", "Contacts permission not granted for WhatsApp VoIP lookup");
            return false;
        }

        String mimeType = isVideo ? "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
                                  : "vnd.android.cursor.item/vnd.com.whatsapp.voip.call";

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    ContactsContract.Data.CONTENT_URI,
                    new String[]{
                            ContactsContract.Data._ID,
                            ContactsContract.Data.DISPLAY_NAME,
                            ContactsContract.Data.DATA1,
                            ContactsContract.Data.DATA3
                    },
                    ContactsContract.Data.MIMETYPE + " = ?",
                    new String[]{mimeType},
                    null
            );

            if (cursor != null) {
                String q = (contactName != null) ? contactName.trim().toLowerCase() : "";
                String phoneDigits = (phoneNumber != null) ? phoneNumber.replaceAll("[^0-9]", "") : "";
                if (phoneDigits.isEmpty() && contactName != null) {
                    String extracted = contactName.replaceAll("[^0-9]", "");
                    if (extracted.length() >= 6) {
                        phoneDigits = extracted;
                    }
                }
                String phoneSuffix = (phoneDigits.length() >= 7) ? phoneDigits.substring(phoneDigits.length() - 7) : phoneDigits;

                long matchedDataId = -1;

                // Pass 1: exact contact name match
                if (!q.isEmpty()) {
                    while (cursor.moveToNext()) {
                        int nameCol = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);
                        if (nameCol != -1) {
                            String name = cursor.getString(nameCol);
                            if (name != null && name.trim().equalsIgnoreCase(q)) {
                                matchedDataId = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID));
                                break;
                            }
                        }
                    }
                }

                // Pass 2: contains match on name
                if (matchedDataId == -1 && !q.isEmpty()) {
                    cursor.moveToPosition(-1);
                    while (cursor.moveToNext()) {
                        int nameCol = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);
                        if (nameCol != -1) {
                            String name = cursor.getString(nameCol);
                            if (name != null && name.toLowerCase().contains(q)) {
                                matchedDataId = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID));
                                break;
                            }
                        }
                    }
                }

                // Pass 3: phone match on DATA1, DATA3
                if (matchedDataId == -1 && !phoneSuffix.isEmpty()) {
                    cursor.moveToPosition(-1);
                    while (cursor.moveToNext()) {
                        int data1Col = cursor.getColumnIndex(ContactsContract.Data.DATA1);
                        int data3Col = cursor.getColumnIndex(ContactsContract.Data.DATA3);

                        if (data1Col != -1) {
                            String d1 = cursor.getString(data1Col);
                            if (d1 != null) {
                                String cleanD1 = d1.replaceAll("[^0-9]", "");
                                if (cleanD1.endsWith(phoneSuffix) || cleanD1.contains(phoneDigits)) {
                                    matchedDataId = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID));
                                    break;
                                }
                            }
                        }
                        if (data3Col != -1) {
                            String d3 = cursor.getString(data3Col);
                            if (d3 != null) {
                                String cleanD3 = d3.replaceAll("[^0-9]", "");
                                if (cleanD3.endsWith(phoneSuffix) || cleanD3.contains(phoneDigits)) {
                                    matchedDataId = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID));
                                    break;
                                }
                            }
                        }
                    }
                }

                if (matchedDataId != -1) {
                    AppLogger.i("ACTION", "Found WhatsApp VoIP direct call Data ID: " + matchedDataId + " for '" + contactName + "'");
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, matchedDataId), mimeType);
                    intent.setPackage("com.whatsapp");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    try {
                        net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp");
                        context.startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        try {
                            intent.setPackage("com.whatsapp.w4b");
                            net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp.w4b");
                            context.startActivity(intent);
                            return true;
                        } catch (Exception e2) {
                            AppLogger.e("ACTION", "WhatsApp direct VoIP launch failed: " + e2.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.e("ACTION", "Error querying WhatsApp VoIP direct call: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    public static void executePhoneDial(Context context, String contact) {
        String phone = null;
        String cleanNum = contact.replaceAll("[^0-9+]", "");
        if (cleanNum.length() >= 3) {
            phone = cleanNum;
        } else {
            phone = findPhoneNumberForContact(context, contact);
        }
        if (phone != null && !phone.isEmpty()) {
            try {
                TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (tm != null) {
                    String defaultDialer = tm.getDefaultDialerPackage();
                    if (defaultDialer != null && !defaultDialer.isEmpty()) {
                        net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().notifyAppLaunchedFromConsole(defaultDialer);
                    }
                }
            } catch (Exception ignored) {}

            boolean hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
            Intent intent = new Intent(hasCallPerm ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                AppLogger.e("ACTION", "Failed to dial phone number: " + phone, e);
            }

            if (!hasCallPerm && context instanceof android.app.Activity) {
                androidx.core.app.ActivityCompat.requestPermissions((android.app.Activity) context, new String[]{Manifest.permission.CALL_PHONE}, 201);
            }
        } else {
            launchAppByName(context, "phone");
        }
    }
}
