package net.nhiroki.bluelineconsole.commandSearchers.eachSearcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import androidx.core.content.ContextCompat;
import android.util.TypedValue;
import android.graphics.Typeface;

import net.nhiroki.bluelineconsole.agent.AgentActionEngine;
import net.nhiroki.bluelineconsole.applicationMain.MainActivity;
import net.nhiroki.bluelineconsole.commands.logs.AppLogger;
import net.nhiroki.bluelineconsole.interfaces.CandidateEntry;
import net.nhiroki.bluelineconsole.interfaces.CommandSearcher;
import net.nhiroki.bluelineconsole.interfaces.EventLauncher;

import java.util.ArrayList;
import java.util.List;

public class AICommandSearcher implements CommandSearcher {
    @Override
    public void refresh(Context context) {}

    @Override
    public void close() {}

    @Override
    public boolean isPrepared() {
        return true;
    }

    @Override
    public void waitUntilPrepared() {}

    @Override
    @NonNull
    public List<CandidateEntry> searchCandidateEntries(String query, Context context) {
        List<CandidateEntry> candidates = new ArrayList<>();

        String targetQuery = query.trim();
        String lower = targetQuery.toLowerCase();

        // 1. Check for direct agent action (e.g. "open youtube and search lofi", "search lofi on youtube", "ai open camera")
        AgentActionEngine.Action directAction = parseDirectAction(targetQuery);
        if (directAction != null) {
            AppLogger.i("AGENT", "Direct action candidate created: " + directAction.type + " " + (directAction.appName != null ? directAction.appName : directAction.target));
            candidates.add(new AgentActionCandidateEntry(directAction));
        }

        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_ai_enabled", false);
        if (!enabled) {
            return candidates;
        }

        // 2. Screen capture queries: "?screen <question>" or "ai screen <question>"
        if (lower.startsWith("?screen") || lower.startsWith("ai screen")) {
            String question = "";
            if (lower.startsWith("?screen")) {
                question = targetQuery.substring("?screen".length()).trim();
            } else if (lower.startsWith("ai screen")) {
                question = targetQuery.substring("ai screen".length()).trim();
            }
            candidates.add(new AICandidateEntry(question, true));
            return candidates;
        }

        // 3. Clear chat memory: "?clear" or "ai clear"
        if (lower.equals("?clear") || lower.equals("ai clear")) {
            candidates.add(new AIClearHistoryCandidateEntry());
            return candidates;
        }

        // 4. Standard queries: "ai <question>", "? <question>"
        String question = null;
        if (lower.startsWith("ai ")) {
            question = targetQuery.substring(3).trim();
        } else if (targetQuery.startsWith("? ")) {
            question = targetQuery.substring(2).trim();
        } else if (targetQuery.equals("?") || lower.equals("ai")) {
            question = "";
        }

        if (question != null) {
            candidates.add(new AICandidateEntry(question, false));

            // If entering prompt mode without a question, provide screen analysis shortcut and clear option
            if (question.isEmpty()) {
                candidates.add(new AICandidateEntry("", true));
                if (!AIChatSession.getInstance().isEmpty()) {
                    candidates.add(new AIClearHistoryCandidateEntry());
                }
            }
        }

        return candidates;
    }

    public static AgentActionEngine.Action parseDirectAction(String rawQuery) {
        if (rawQuery == null) return null;
        String q = rawQuery.trim();
        String low = q.toLowerCase();

        if (low.startsWith("ai ")) {
            q = q.substring(3).trim();
            low = q.toLowerCase();
        } else if (low.startsWith("? ")) {
            q = q.substring(2).trim();
            low = q.toLowerCase();
        }

        if (q.isEmpty()) return null;

        // 1. "open <app> and search <query>"
        if (low.startsWith("open ") && low.contains(" and search ")) {
            int andIdx = low.indexOf(" and search ");
            String app = q.substring(5, andIdx).trim();
            String searchQ = q.substring(andIdx + " and search ".length()).trim();
            if (!app.isEmpty() && !searchQ.isEmpty()) {
                return new AgentActionEngine.Action("SEARCH_APP", app, searchQ);
            }
        }

        // 2. "search <query> on/in <app>" or "search <app> for <query>" or "search <query>"
        if (low.startsWith("search ")) {
            int forIdx = low.indexOf(" for ");
            if (forIdx > 7) {
                String app = q.substring(7, forIdx).trim();
                String searchQ = q.substring(forIdx + " for ".length()).trim();
                if (!app.isEmpty() && !searchQ.isEmpty()) {
                    return new AgentActionEngine.Action("SEARCH_APP", app, searchQ);
                }
            }

            int onIdx = low.lastIndexOf(" on ");
            int inIdx = low.lastIndexOf(" in ");
            int splitIdx = Math.max(onIdx, inIdx);
            if (splitIdx > 7) {
                String searchQ = q.substring(7, splitIdx).trim();
                String app = q.substring(splitIdx + 4).trim();
                if (!searchQ.isEmpty() && !app.isEmpty()) {
                    return new AgentActionEngine.Action("SEARCH_APP", app, searchQ);
                }
            }

            // Generic web search
            String generalQuery = q.substring(7).trim();
            if (!generalQuery.isEmpty()) {
                return new AgentActionEngine.Action("SEARCH_APP", "Google", generalQuery);
            }
        }

        // 3. WhatsApp shortcuts: "whatsapp <query>", "wa <query>"
        if (low.startsWith("whatsapp ") || low.startsWith("wa ")) {
            int space = q.indexOf(' ');
            String sub = q.substring(space + 1).trim();
            if (!sub.isEmpty()) {
                AgentActionEngine.MessageDetails details = AgentActionEngine.parseMessageDetails(sub);
                return new AgentActionEngine.Action("SEND_MESSAGE", "whatsapp", details.recipient, details.message);
            }
        }

        // 4. Telegram shortcuts: "telegram <query>", "tg <query>"
        if (low.startsWith("telegram ") || low.startsWith("tg ")) {
            int space = q.indexOf(' ');
            String sub = q.substring(space + 1).trim();
            if (!sub.isEmpty()) {
                AgentActionEngine.MessageDetails details = AgentActionEngine.parseMessageDetails(sub);
                return new AgentActionEngine.Action("SEND_MESSAGE", "telegram", details.recipient, details.message);
            }
        }

        // 5. SMS shortcuts: "sms <query>", "msg <query>"
        if (low.startsWith("sms ") || low.startsWith("msg ")) {
            int space = q.indexOf(' ');
            String sub = q.substring(space + 1).trim();
            if (!sub.isEmpty()) {
                AgentActionEngine.MessageDetails details = AgentActionEngine.parseMessageDetails(sub);
                return new AgentActionEngine.Action("SEND_MESSAGE", "sms", details.recipient, details.message);
            }
        }

        // 6. Direct "send <msg> to <contact> [on <app>]"
        if (low.startsWith("send ") && low.contains(" to ")) {
            String app = "whatsapp";
            String content = q;
            if (low.contains(" on whatsapp") || low.contains(" via whatsapp") || low.contains(" in whatsapp")) {
                app = "whatsapp";
                int idx = low.lastIndexOf(" on whatsapp");
                if (idx == -1) idx = low.lastIndexOf(" via whatsapp");
                if (idx == -1) idx = low.lastIndexOf(" in whatsapp");
                content = q.substring(0, idx).trim();
            } else if (low.contains(" on telegram") || low.contains(" via telegram") || low.contains(" in telegram")) {
                app = "telegram";
                int idx = low.lastIndexOf(" on telegram");
                if (idx == -1) idx = low.lastIndexOf(" via telegram");
                if (idx == -1) idx = low.lastIndexOf(" in telegram");
                content = q.substring(0, idx).trim();
            } else if (low.contains(" on sms") || low.contains(" via sms")) {
                app = "sms";
                int idx = low.lastIndexOf(" on sms");
                if (idx == -1) idx = low.lastIndexOf(" via sms");
                content = q.substring(0, idx).trim();
            }
            AgentActionEngine.MessageDetails details = AgentActionEngine.parseMessageDetails(content);
            return new AgentActionEngine.Action("SEND_MESSAGE", app, details.recipient, details.message);
        }

        // 7. "play <query>" -> YouTube search
        if (low.startsWith("play ")) {
            String song = q.substring(5).trim();
            if (!song.isEmpty()) {
                return new AgentActionEngine.Action("SEARCH_APP", "YouTube", song);
            }
        }

        // 8. "youtube <query>", "yt <query>"
        if (low.startsWith("youtube ") || low.startsWith("yt ")) {
            int space = q.indexOf(' ');
            String ytQ = q.substring(space + 1).trim();
            if (!ytQ.isEmpty()) {
                return new AgentActionEngine.Action("SEARCH_APP", "YouTube", ytQ);
            }
        }

        // 9. "spotify <query>"
        if (low.startsWith("spotify ")) {
            String spQ = q.substring(8).trim();
            if (!spQ.isEmpty()) {
                return new AgentActionEngine.Action("SEARCH_APP", "Spotify", spQ);
            }
        }

        // 10. "maps <query>", "map <query>"
        if (low.startsWith("maps ") || low.startsWith("map ")) {
            int space = q.indexOf(' ');
            String mapQ = q.substring(space + 1).trim();
            if (!mapQ.isEmpty()) {
                return new AgentActionEngine.Action("SEARCH_APP", "Maps", mapQ);
            }
        }

        // 11. "click <target>" or "tap <target>"
        if (low.startsWith("click ") || low.startsWith("tap ")) {
            int spaceIdx = q.indexOf(' ');
            String target = q.substring(spaceIdx + 1).trim();
            if (!target.isEmpty()) {
                return new AgentActionEngine.Action("CLICK", target);
            }
        }

        // 12. "type <text>"
        if (low.startsWith("type ")) {
            String txt = q.substring(5).trim();
            if (!txt.isEmpty()) {
                return new AgentActionEngine.Action("TYPE", null, txt);
            }
        }

        // 13. "open <app>" or "launch <app>"
        if (low.startsWith("open ") || low.startsWith("launch ")) {
            int spaceIdx = q.indexOf(' ');
            String rest = q.substring(spaceIdx + 1).trim();
            String restLow = rest.toLowerCase();

            // Check if it's "open <app> find <query>"
            if (restLow.contains(" find ")) {
                int findIdx = restLow.indexOf(" find ");
                String app = rest.substring(0, findIdx).trim();
                String searchQ = rest.substring(findIdx + 6).trim();
                if (!app.isEmpty() && !searchQ.isEmpty()) {
                    if (app.equalsIgnoreCase("whatsapp") || app.equalsIgnoreCase("wa") ||
                        app.equalsIgnoreCase("telegram") || app.equalsIgnoreCase("tg") ||
                        app.equalsIgnoreCase("sms") || app.equalsIgnoreCase("messages")) {
                        AgentActionEngine.MessageDetails details = AgentActionEngine.parseMessageDetails(searchQ);
                        return new AgentActionEngine.Action("SEND_MESSAGE", app, details.recipient, details.message);
                    }
                    return new AgentActionEngine.Action("SEARCH_APP", app, searchQ);
                }
            }

            // Check if it's "open <app> send <query>" or "open <app> and send <query>"
            if (restLow.contains(" and send ") || restLow.contains(" send ") || restLow.contains(" sent ")) {
                int sIdx = restLow.indexOf(" and send ");
                int len = " and send ".length();
                if (sIdx == -1) {
                    sIdx = restLow.indexOf(" send ");
                    len = " send ".length();
                }
                if (sIdx == -1) {
                    sIdx = restLow.indexOf(" sent ");
                    len = " sent ".length();
                }
                String app = rest.substring(0, sIdx).trim();
                String sub = rest.substring(sIdx + len).trim();
                if (!app.isEmpty()) {
                    AgentActionEngine.MessageDetails details = AgentActionEngine.parseMessageDetails(sub);
                    return new AgentActionEngine.Action("SEND_MESSAGE", app, details.recipient, details.message);
                }
            }

            // Check if it's "open <app> search <query>"
            if (restLow.contains(" search ")) {
                int searchIdx = restLow.indexOf(" search ");
                String app = rest.substring(0, searchIdx).trim();
                String searchQ = rest.substring(searchIdx + 8).trim();
                if (!app.isEmpty() && !searchQ.isEmpty()) {
                    return new AgentActionEngine.Action("SEARCH_APP", app, searchQ);
                }
            }

            // Simple "open <app>" without secondary command keywords
            if (!rest.isEmpty() && !restLow.contains(" and ") && !restLow.contains(" for ")
                    && !restLow.contains(" send ") && !restLow.contains(" msg ") && !restLow.contains(" message ")
                    && !restLow.contains(" tell ") && !restLow.contains(" call ") && !restLow.contains(" to ")) {
                return new AgentActionEngine.Action("OPEN_APP", rest, null);
            }
        }

        return null;
    }

    public static class AgentActionCandidateEntry implements CandidateEntry {
        private final AgentActionEngine.Action action;

        public AgentActionCandidateEntry(AgentActionEngine.Action action) {
            this.action = action;
        }

        public AgentActionEngine.Action getAction() {
            return action;
        }

        private static String capitalize(String str) {
            if (str == null || str.isEmpty()) return "";
            return Character.toUpperCase(str.charAt(0)) + str.substring(1);
        }

        @Override
        public String getTitle() {
            if ("SEARCH_APP".equalsIgnoreCase(action.type)) {
                String app = capitalize(action.appName);
                if ("chrome".equalsIgnoreCase(app) || "browser".equalsIgnoreCase(app) || "google".equalsIgnoreCase(app)) {
                    app = "Google";
                }
                return "⚡ Agent: Search " + app + " for \"" + action.query + "\"";
            } else if ("OPEN_APP".equalsIgnoreCase(action.type)) {
                return "⚡ Agent: Open " + capitalize(action.appName);
            } else if ("SEND_MESSAGE".equalsIgnoreCase(action.type)) {
                String app = capitalize(action.appName);
                if (action.target != null && !action.target.isEmpty() && action.query != null && !action.query.isEmpty()) {
                    return "⚡ Agent: Send \"" + action.query + "\" to " + action.target + " on " + app;
                } else if (action.target != null && !action.target.isEmpty()) {
                    return "⚡ Agent: Chat with " + action.target + " on " + app;
                } else if (action.query != null && !action.query.isEmpty()) {
                    return "⚡ Agent: Send \"" + action.query + "\" on " + app;
                }
                return "⚡ Agent: Message on " + app;
            } else if ("CLICK".equalsIgnoreCase(action.type)) {
                return "⚡ Agent: Click \"" + action.target + "\"";
            } else if ("TYPE".equalsIgnoreCase(action.type)) {
                return "⚡ Agent: Type \"" + action.query + "\"";
            } else if ("OPEN_URL".equalsIgnoreCase(action.type)) {
                return "⚡ Agent: Open URL " + action.query;
            }
            return "⚡ Agent: Run Action";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            TextView tv = new TextView(mainActivity);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setTextColor(mainActivity.getAccentColor());
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setPadding(0, 4, 0, 8);
            if ("SEARCH_APP".equalsIgnoreCase(action.type)) {
                String app = capitalize(action.appName);
                if ("chrome".equalsIgnoreCase(app) || "browser".equalsIgnoreCase(app) || "google".equalsIgnoreCase(app)) {
                    app = "Google";
                }
                tv.setText("▶ Tap or Enter to search on " + app);
            } else if ("SEND_MESSAGE".equalsIgnoreCase(action.type)) {
                String app = capitalize(action.appName);
                if (action.target != null && !action.target.isEmpty() && action.query != null && !action.query.isEmpty()) {
                    tv.setText("▶ Tap or Enter to send message to " + action.target + " via " + app);
                } else if (action.target != null && !action.target.isEmpty()) {
                    tv.setText("▶ Tap or Enter to chat with " + action.target + " on " + app);
                } else {
                    tv.setText("▶ Tap or Enter to share message via " + app);
                }
            } else if ("OPEN_APP".equalsIgnoreCase(action.type)) {
                tv.setText("▶ Tap or Enter to launch " + capitalize(action.appName));
            } else if ("CLICK".equalsIgnoreCase(action.type)) {
                tv.setText("▶ Tap or Enter to click via Accessibility Service");
            } else {
                tv.setText("▶ Tap or Enter to execute action");
            }
            return tv;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLogger.i("AGENT", "Launching agent action: " + action.type + " (app=" + action.appName + ", target=" + action.target + ", query=" + action.query + ")");
                AgentActionEngine.executeAction(activity, action);
                activity.finishIfNotHome();
            };
        }

        @Override public boolean hasLongView() { return false; }
        @Override public Drawable getIcon(Context context) {
            return ContextCompat.getDrawable(context, net.nhiroki.bluelineconsole.R.drawable.ic_agent_cyber);
        }
        @Override public boolean hasEvent() { return true; }
        @Override public boolean isSubItem() { return false; }
        @Override public boolean viewIsRecyclable() { return true; }
    }

    public static class AIClearHistoryCandidateEntry implements CandidateEntry {
        @Override
        public String getTitle() {
            int count = AIChatSession.getInstance().size();
            return "Clear Gemini Chat History (" + count + " messages in memory)";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            TextView tv = new TextView(mainActivity);
            tv.setText(getTitle());
            tv.setTextColor(android.graphics.Color.parseColor("#ff0055"));
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            tv.setPadding(0, 16, 0, 16);
            return tv;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AIChatSession.getInstance().clear();
                Toast.makeText(activity, "AI conversation history cleared.", Toast.LENGTH_SHORT).show();
                activity.changeInputText("");
            };
        }

        @Override public boolean hasLongView() { return false; }
        @Override public Drawable getIcon(Context context) { return null; }
        @Override public boolean hasEvent() { return true; }
        @Override public boolean isSubItem() { return false; }
        @Override public boolean viewIsRecyclable() { return true; }
    }
}
