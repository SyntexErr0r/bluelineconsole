package net.nhiroki.bluelineconsole.contacts;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import net.nhiroki.bluelineconsole.agent.AgentActionEngine;
import net.nhiroki.bluelineconsole.commands.logs.AppLogger;
import net.nhiroki.bluelineconsole.wrapperForAndroid.ContactsReader;

import org.json.JSONException;
import org.json.JSONObject;

import net.nhiroki.bluelineconsole.agent.BlueLineAgentService;
import net.nhiroki.bluelineconsole.applock.AppLockManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContactManager {
    private static final String PREF_NAME = "pref_contact_manager";

    public static final String CALL_METHOD_DEFAULT = "DEFAULT";
    public static final String CALL_METHOD_PHONE = "PHONE";
    public static final String CALL_METHOD_WHATSAPP_VOICE = "WHATSAPP_VOICE";
    public static final String CALL_METHOD_WHATSAPP_VIDEO = "WHATSAPP_VIDEO";
    public static final String CALL_METHOD_TELEGRAM = "TELEGRAM";

    public static final String MSG_METHOD_DEFAULT = "DEFAULT";
    public static final String MSG_METHOD_SMS = "SMS";
    public static final String MSG_METHOD_WHATSAPP = "WHATSAPP";
    public static final String MSG_METHOD_TELEGRAM = "TELEGRAM";

    public static final String KEY_GLOBAL_DEFAULT_CALL_METHOD = "pref_contact_global_call_method";
    public static final String KEY_GLOBAL_DEFAULT_MSG_METHOD = "pref_contact_global_msg_method";
    public static final String KEY_ONLY_SEARCH_ON_COMMAND = "pref_contact_only_search_on_command";
    private static final String KEY_PINNED_CONTACTS = "pref_contact_pinned_keys";
    private static final String KEY_CUSTOM_CONFIGS = "pref_contact_custom_configs";

    private static ContactManager sInstance = null;

    private boolean mInitialized = false;
    private String mGlobalCallMethod = CALL_METHOD_PHONE;
    private String mGlobalMsgMethod = MSG_METHOD_WHATSAPP;
    private boolean mOnlySearchOnCommand = true;
    private final Set<String> mPinnedKeys = new HashSet<>();
    private final Map<String, ContactCustomConfig> mCustomConfigs = new HashMap<>();

    public static class ContactCustomConfig {
        public String key = "";
        public String displayName = "";
        public String preferredCallMethod = CALL_METHOD_DEFAULT;
        public String preferredMsgMethod = MSG_METHOD_DEFAULT;
        public String telegramUsername = "";
        public boolean isPinned = false;

        public ContactCustomConfig() {}

        public ContactCustomConfig(String key, String displayName, String preferredCallMethod,
                                   String preferredMsgMethod, String telegramUsername, boolean isPinned) {
            this.key = key;
            this.displayName = displayName;
            this.preferredCallMethod = preferredCallMethod;
            this.preferredMsgMethod = preferredMsgMethod;
            this.telegramUsername = telegramUsername;
            this.isPinned = isPinned;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("key", key);
                obj.put("name", displayName);
                obj.put("call", preferredCallMethod);
                obj.put("msg", preferredMsgMethod);
                obj.put("tg", telegramUsername);
                obj.put("pinned", isPinned);
            } catch (JSONException ignored) {}
            return obj;
        }

        public static ContactCustomConfig fromJson(JSONObject obj) {
            ContactCustomConfig cfg = new ContactCustomConfig();
            cfg.key = obj.optString("key", "");
            cfg.displayName = obj.optString("name", "");
            cfg.preferredCallMethod = obj.optString("call", CALL_METHOD_DEFAULT);
            cfg.preferredMsgMethod = obj.optString("msg", MSG_METHOD_DEFAULT);
            cfg.telegramUsername = obj.optString("tg", "");
            cfg.isPinned = obj.optBoolean("pinned", false);
            return cfg;
        }
    }

    private ContactManager() {}

    public static synchronized ContactManager getInstance() {
        if (sInstance == null) {
            sInstance = new ContactManager();
        }
        return sInstance;
    }

    private synchronized void ensureInitialized(Context context) {
        if (mInitialized || context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mGlobalCallMethod = sp.getString(KEY_GLOBAL_DEFAULT_CALL_METHOD, CALL_METHOD_PHONE);
        mGlobalMsgMethod = sp.getString(KEY_GLOBAL_DEFAULT_MSG_METHOD, MSG_METHOD_WHATSAPP);
        mOnlySearchOnCommand = sp.getBoolean(KEY_ONLY_SEARCH_ON_COMMAND, true);

        Set<String> pinned = sp.getStringSet(KEY_PINNED_CONTACTS, null);
        mPinnedKeys.clear();
        if (pinned != null) {
            mPinnedKeys.addAll(pinned);
        }

        mCustomConfigs.clear();
        String jsonStr = sp.getString(KEY_CUSTOM_CONFIGS, "{}");
        try {
            JSONObject root = new JSONObject(jsonStr);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject obj = root.getJSONObject(k);
                mCustomConfigs.put(k, ContactCustomConfig.fromJson(obj));
            }
        } catch (Exception e) {
            AppLogger.e("CONTACT_MGR", "Error loading contact configs", e);
        }

        mInitialized = true;
    }

    private synchronized void savePreferences(Context context) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putString(KEY_GLOBAL_DEFAULT_CALL_METHOD, mGlobalCallMethod);
        ed.putString(KEY_GLOBAL_DEFAULT_MSG_METHOD, mGlobalMsgMethod);
        ed.putBoolean(KEY_ONLY_SEARCH_ON_COMMAND, mOnlySearchOnCommand);
        ed.putStringSet(KEY_PINNED_CONTACTS, new HashSet<>(mPinnedKeys));

        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, ContactCustomConfig> e : mCustomConfigs.entrySet()) {
                root.put(e.getKey(), e.getValue().toJson());
            }
        } catch (Exception ignored) {}
        ed.putString(KEY_CUSTOM_CONFIGS, root.toString());
        ed.apply();
    }

    public static String getContactKey(ContactsReader.Contact contact) {
        if (contact == null) return "";
        if (contact.displayName != null && !contact.displayName.trim().isEmpty()) {
            return contact.displayName.trim().toLowerCase();
        }
        if (!contact.phoneNumbers.isEmpty()) {
            return cleanPhoneNumber(contact.phoneNumbers.get(0));
        }
        return "unknown";
    }

    public synchronized String getGlobalCallMethod(Context context) {
        ensureInitialized(context);
        return mGlobalCallMethod;
    }

    public synchronized void setGlobalCallMethod(Context context, String method) {
        ensureInitialized(context);
        mGlobalCallMethod = method;
        savePreferences(context);
    }

    public synchronized String getGlobalMsgMethod(Context context) {
        ensureInitialized(context);
        return mGlobalMsgMethod;
    }

    public synchronized void setGlobalMsgMethod(Context context, String method) {
        ensureInitialized(context);
        mGlobalMsgMethod = method;
        savePreferences(context);
    }

    public synchronized boolean isOnlySearchOnCommand(Context context) {
        ensureInitialized(context);
        return mOnlySearchOnCommand;
    }

    public synchronized void setOnlySearchOnCommand(Context context, boolean onlyOnCommand) {
        ensureInitialized(context);
        mOnlySearchOnCommand = onlyOnCommand;
        savePreferences(context);
    }

    public synchronized boolean isPinned(Context context, String contactKey) {
        ensureInitialized(context);
        if (contactKey == null) return false;
        return mPinnedKeys.contains(contactKey.toLowerCase());
    }

    public synchronized void setPinned(Context context, String contactKey, boolean pinned) {
        ensureInitialized(context);
        if (contactKey == null || contactKey.isEmpty()) return;
        String k = contactKey.toLowerCase();
        if (pinned) {
            mPinnedKeys.add(k);
        } else {
            mPinnedKeys.remove(k);
        }
        ContactCustomConfig cfg = mCustomConfigs.get(k);
        if (cfg != null) {
            cfg.isPinned = pinned;
        }
        savePreferences(context);
    }

    public synchronized Set<String> getPinnedKeys(Context context) {
        ensureInitialized(context);
        return Collections.unmodifiableSet(new HashSet<>(mPinnedKeys));
    }

    public synchronized ContactCustomConfig getCustomConfig(Context context, String contactKey) {
        ensureInitialized(context);
        if (contactKey == null) return null;
        return mCustomConfigs.get(contactKey.toLowerCase());
    }

    public synchronized void setCustomConfig(Context context, String contactKey, ContactCustomConfig config) {
        ensureInitialized(context);
        if (contactKey == null || contactKey.isEmpty()) return;
        String k = contactKey.toLowerCase();
        if (config == null) {
            mCustomConfigs.remove(k);
        } else {
            mCustomConfigs.put(k, config);
            if (config.isPinned) {
                mPinnedKeys.add(k);
            } else {
                mPinnedKeys.remove(k);
            }
        }
        savePreferences(context);
    }

    public synchronized String getEffectiveCallMethod(Context context, ContactsReader.Contact contact) {
        ensureInitialized(context);
        String key = getContactKey(contact);
        ContactCustomConfig cfg = mCustomConfigs.get(key);
        if (cfg != null && cfg.preferredCallMethod != null && !cfg.preferredCallMethod.equals(CALL_METHOD_DEFAULT)) {
            return cfg.preferredCallMethod;
        }
        return mGlobalCallMethod;
    }

    public synchronized String getEffectiveMsgMethod(Context context, ContactsReader.Contact contact) {
        ensureInitialized(context);
        String key = getContactKey(contact);
        ContactCustomConfig cfg = mCustomConfigs.get(key);
        if (cfg != null && cfg.preferredMsgMethod != null && !cfg.preferredMsgMethod.equals(MSG_METHOD_DEFAULT)) {
            return cfg.preferredMsgMethod;
        }
        return mGlobalMsgMethod;
    }

    public synchronized String getEffectiveCallMethod(Context context, String contactNameOrNumber) {
        ensureInitialized(context);
        ContactsReader.Contact c = findContact(context, contactNameOrNumber);
        if (c != null) {
            return getEffectiveCallMethod(context, c);
        }
        return mGlobalCallMethod;
    }

    public synchronized String getEffectiveMsgMethod(Context context, String contactNameOrNumber) {
        ensureInitialized(context);
        ContactsReader.Contact c = findContact(context, contactNameOrNumber);
        if (c != null) {
            return getEffectiveMsgMethod(context, c);
        }
        return mGlobalMsgMethod;
    }

    public ContactsReader.Contact findContact(Context context, String query) {
        if (context == null || query == null || query.trim().isEmpty()) return null;
        if (!ContactsReader.appHasReadContactsPermission(context)) return null;

        String q = query.trim().toLowerCase();
        try {
            List<ContactsReader.Contact> contacts = ContactsReader.fetchAllContacts(context);
            if (contacts == null || contacts.isEmpty()) return null;

            // Pass 1: exact name match
            for (ContactsReader.Contact c : contacts) {
                if (c.displayName != null && c.displayName.trim().equalsIgnoreCase(q)) {
                    return c;
                }
            }

            // Pass 2: starts with match
            for (ContactsReader.Contact c : contacts) {
                if (c.displayName != null && c.displayName.toLowerCase().startsWith(q)) {
                    return c;
                }
            }

            // Pass 3: contains match
            for (ContactsReader.Contact c : contacts) {
                if (c.displayName != null && c.displayName.toLowerCase().contains(q)) {
                    return c;
                }
            }

            // Pass 4: phone number match
            String cleanQ = cleanPhoneNumber(q);
            if (!cleanQ.isEmpty() && cleanQ.length() >= 3) {
                for (ContactsReader.Contact c : contacts) {
                    if (c.phoneNumbers != null) {
                        for (String phone : c.phoneNumbers) {
                            if (cleanPhoneNumber(phone).endsWith(cleanQ)) {
                                return c;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.e("CONTACTS", "Error finding contact: " + query, e);
        }
        return null;
    }

    public static String cleanPhoneNumber(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^0-9+]", "");
    }

    public static String getPrimaryPhoneNumber(ContactsReader.Contact contact) {
        if (contact == null || contact.phoneNumbers == null || contact.phoneNumbers.isEmpty()) {
            return "";
        }
        return contact.phoneNumbers.get(0);
    }

    public void executeCall(Context context, ContactsReader.Contact contact) {
        String method = getEffectiveCallMethod(context, contact);
        executeCallWithMethod(context, contact, method);
    }

    public void executeCallWithMethod(Context context, ContactsReader.Contact contact, String method) {
        if (context == null || contact == null) return;
        String phone = getPrimaryPhoneNumber(contact);
        String cleanPhone = cleanPhoneNumber(phone);
        String contactName = contact.displayName;

        switch (method) {
            case CALL_METHOD_WHATSAPP_VOICE: {
                AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp");
                AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp.w4b");
                boolean success = AgentActionEngine.launchWhatsAppDirectCallIntent(context, contactName, false);
                if (!success && !cleanPhone.isEmpty()) {
                    Uri uri = Uri.parse("https://api.whatsapp.com/send?phone=" + Uri.encode(cleanPhone));
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.setPackage("com.whatsapp");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(intent);
                        if (BlueLineAgentService.isServiceConnected()) {
                            BlueLineAgentService.getInstance().scheduleWhatsAppCallClick(false);
                        } else {
                            Toast.makeText(context, "Opening WhatsApp for " + contactName, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        launchDialer(context, phone);
                    }
                } else if (!success) {
                    launchDialer(context, phone);
                }
                break;
            }
            case CALL_METHOD_WHATSAPP_VIDEO: {
                AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp");
                AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp.w4b");
                boolean success = AgentActionEngine.launchWhatsAppDirectCallIntent(context, contactName, true);
                if (!success && !cleanPhone.isEmpty()) {
                    Uri uri = Uri.parse("https://api.whatsapp.com/send?phone=" + Uri.encode(cleanPhone));
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.setPackage("com.whatsapp");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(intent);
                        if (BlueLineAgentService.isServiceConnected()) {
                            BlueLineAgentService.getInstance().scheduleWhatsAppCallClick(true);
                        } else {
                            Toast.makeText(context, "Opening WhatsApp for " + contactName, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        launchDialer(context, phone);
                    }
                } else if (!success) {
                    launchDialer(context, phone);
                }
                break;
            }
            case CALL_METHOD_TELEGRAM: {
                AppLockManager.getInstance().notifyAppLaunchedFromConsole("org.telegram.messenger");
                ContactCustomConfig cfg = getCustomConfig(context, getContactKey(contact));
                String tgUser = (cfg != null && !cfg.telegramUsername.isEmpty()) ? cfg.telegramUsername : "";
                if (!tgUser.isEmpty()) {
                    String username = tgUser.startsWith("@") ? tgUser.substring(1) : tgUser;
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + username));
                    intent.setPackage("org.telegram.messenger");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(intent);
                        return;
                    } catch (Exception ignored) {}
                }
                AgentActionEngine.executeTelegram(context, contactName, "");
                break;
            }
            case CALL_METHOD_PHONE:
            default: {
                launchDialer(context, phone);
                break;
            }
        }
    }

    public void executeMessage(Context context, ContactsReader.Contact contact, String defaultText) {
        String method = getEffectiveMsgMethod(context, contact);
        executeMessageWithMethod(context, contact, method, defaultText);
    }

    public void executeMessageWithMethod(Context context, ContactsReader.Contact contact, String method, String defaultText) {
        if (context == null || contact == null) return;
        String phone = getPrimaryPhoneNumber(contact);
        String cleanPhone = cleanPhoneNumber(phone);
        String contactName = contact.displayName;

        switch (method) {
            case MSG_METHOD_WHATSAPP: {
                AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp");
                AppLockManager.getInstance().notifyAppLaunchedFromConsole("com.whatsapp.w4b");
                if (!cleanPhone.isEmpty()) {
                    String url = "https://api.whatsapp.com/send?phone=" + Uri.encode(cleanPhone);
                    if (defaultText != null && !defaultText.isEmpty()) {
                        url += "&text=" + Uri.encode(defaultText);
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.setPackage("com.whatsapp");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(intent);
                    } catch (Exception e) {
                        intent.setPackage(null);
                        context.startActivity(intent);
                    }
                } else {
                    AgentActionEngine.executeWhatsApp(context, contactName, defaultText != null ? defaultText : "");
                }
                break;
            }
            case MSG_METHOD_TELEGRAM: {
                AppLockManager.getInstance().notifyAppLaunchedFromConsole("org.telegram.messenger");
                ContactCustomConfig cfg = getCustomConfig(context, getContactKey(contact));
                String tgUser = (cfg != null && !cfg.telegramUsername.isEmpty()) ? cfg.telegramUsername : "";
                if (!tgUser.isEmpty()) {
                    String username = tgUser.startsWith("@") ? tgUser.substring(1) : tgUser;
                    String url = "https://t.me/" + username;
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.setPackage("org.telegram.messenger");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(intent);
                        return;
                    } catch (Exception ignored) {}
                }
                AgentActionEngine.executeTelegram(context, contactName, defaultText != null ? defaultText : "");
                break;
            }
            case MSG_METHOD_SMS:
            default: {
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(cleanPhone.isEmpty() ? phone : cleanPhone)));
                if (defaultText != null && !defaultText.isEmpty()) {
                    intent.putExtra("sms_body", defaultText);
                }
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(context, "Cannot open SMS app", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    private static void launchDialer(Context context, String phone) {
        if (phone == null || phone.isEmpty()) {
            Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Cannot open dialer", Toast.LENGTH_SHORT).show();
        }
    }
}
