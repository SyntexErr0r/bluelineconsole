package net.nhiroki.bluelineconsole.commandSearchers.eachSearcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import net.nhiroki.bluelineconsole.applicationMain.MainActivity;
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

        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_ai_enabled", false);
        if (!enabled) {
            return candidates;
        }

        String targetQuery = query.trim();
        String lower = targetQuery.toLowerCase();

        // 1. Screen capture queries: "?screen <question>" or "ai screen <question>"
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

        // 2. Clear chat memory: "?clear" or "ai clear"
        if (lower.equals("?clear") || lower.equals("ai clear")) {
            candidates.add(new AIClearHistoryCandidateEntry());
            return candidates;
        }

        // 3. Standard queries: "ai <question>", "? <question>"
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
