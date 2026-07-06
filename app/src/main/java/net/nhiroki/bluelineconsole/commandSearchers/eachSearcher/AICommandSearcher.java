package net.nhiroki.bluelineconsole.commandSearchers.eachSearcher;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import net.nhiroki.bluelineconsole.interfaces.CandidateEntry;
import net.nhiroki.bluelineconsole.interfaces.CommandSearcher;

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
        String question = null;

        if (targetQuery.toLowerCase().startsWith("ai ")) {
            question = targetQuery.substring(3).trim();
        } else if (targetQuery.startsWith("? ")) {
            question = targetQuery.substring(2).trim();
        } else if (targetQuery.equals("?")) {
            question = "";
        }

        if (question != null) {
            candidates.add(new AICandidateEntry(question));
        }

        return candidates;
    }
}
