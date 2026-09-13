package net.nhiroki.bluelineconsole.commandSearchers.eachSearcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.nhiroki.bluelineconsole.applicationMain.MainActivity;
import net.nhiroki.bluelineconsole.applicationMain.NotesManagerActivity;
import net.nhiroki.bluelineconsole.interfaces.CandidateEntry;
import net.nhiroki.bluelineconsole.interfaces.CommandSearcher;
import net.nhiroki.bluelineconsole.interfaces.EventLauncher;
import net.nhiroki.bluelineconsole.notes.Note;
import net.nhiroki.bluelineconsole.notes.NotesManager;

import java.util.ArrayList;
import java.util.List;

public class NotesCommandSearcher implements CommandSearcher {

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

    @NonNull
    @Override
    public List<CandidateEntry> searchCandidateEntries(String query, Context context) {
        List<CandidateEntry> candidates = new ArrayList<>();
        if (query == null) return candidates;

        String q = query.trim();
        String qLower = q.toLowerCase();

        if (qLower.equals("note") || qLower.equals("notes") || qLower.equals("/notes") || qLower.equals("/note")) {
            candidates.add(new NotesHubCandidateEntry());
            return candidates;
        }

        if (qLower.startsWith("note ") || qLower.startsWith("notes ") || qLower.startsWith("/note ")) {
            int spaceIdx = q.indexOf(' ');
            String content = (spaceIdx != -1 && spaceIdx < q.length() - 1) ? q.substring(spaceIdx + 1).trim() : "";

            if (!content.isEmpty()) {
                candidates.add(new QuickSaveNoteCandidateEntry(content));
            }
            candidates.add(new NotesHubCandidateEntry());

            // Search existing notes matching content
            if (!content.isEmpty()) {
                List<Note> matches = NotesManager.getInstance().searchNotes(context, content);
                for (int i = 0; i < Math.min(3, matches.size()); i++) {
                    candidates.add(new NoteItemCandidateEntry(matches.get(i)));
                }
            }
            return candidates;
        }

        return candidates;
    }

    public static class NotesHubCandidateEntry implements CandidateEntry {
        @NonNull
        @Override
        public String getTitle() {
            return "📓 Open Cyber Notes Hub";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView countTv = new TextView(mainActivity);
            int count = NotesManager.getInstance().getAllNotes(mainActivity).size();
            countTv.setText(count + " notes saved. Tap to view, write, and manage notes.");
            countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            countTv.setTextColor(mainActivity.getAccentColor());
            countTv.setTypeface(Typeface.MONOSPACE);
            layout.addView(countTv);

            return layout;
        }

        @Override
        public boolean hasLongView() {
            return true;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                Intent intent = new Intent(activity, NotesManagerActivity.class);
                activity.startActivityForResult(intent, MainActivity.REQUEST_CODE_FOR_COMING_BACK);
            };
        }

        @Override public Drawable getIcon(Context context) { return null; }
        @Override public boolean hasEvent() { return true; }
        @Override public boolean isSubItem() { return false; }
        @Override public boolean viewIsRecyclable() { return true; }
    }

    public static class QuickSaveNoteCandidateEntry implements CandidateEntry {
        private final String mContent;

        public QuickSaveNoteCandidateEntry(String content) {
            this.mContent = content;
        }

        @NonNull
        @Override
        public String getTitle() {
            return "➕ Save Quick Note: \"" + (mContent.length() > 30 ? mContent.substring(0, 30) + "..." : mContent) + "\"";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView subTv = new TextView(mainActivity);
            subTv.setText("Tap or press Enter to save to Notes Hub");
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            subTv.setTextColor(Color.parseColor("#00ff99"));
            subTv.setTypeface(Typeface.MONOSPACE);
            layout.addView(subTv);

            return layout;
        }

        @Override
        public boolean hasLongView() {
            return true;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                String title = mContent.length() > 30 ? mContent.substring(0, 30) + "..." : mContent;
                Note note = new Note(title, mContent);
                NotesManager.getInstance().saveNote(activity, note);
                Toast.makeText(activity, "Note saved!", Toast.LENGTH_SHORT).show();
                activity.finish();
            };
        }

        @Override public Drawable getIcon(Context context) { return null; }
        @Override public boolean hasEvent() { return true; }
        @Override public boolean isSubItem() { return false; }
        @Override public boolean viewIsRecyclable() { return true; }
    }

    public static class NoteItemCandidateEntry implements CandidateEntry {
        private final Note mNote;

        public NoteItemCandidateEntry(Note note) {
            this.mNote = note;
        }

        @NonNull
        @Override
        public String getTitle() {
            return "📝 " + (mNote.title.isEmpty() ? "Untitled Note" : mNote.title);
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView snippetTv = new TextView(mainActivity);
            snippetTv.setText(mNote.content.length() > 60 ? mNote.content.substring(0, 60) + "..." : mNote.content);
            snippetTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            snippetTv.setTextColor(Color.parseColor("#888888"));
            snippetTv.setTypeface(Typeface.MONOSPACE);
            layout.addView(snippetTv);

            return layout;
        }

        @Override
        public boolean hasLongView() {
            return true;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                Intent intent = new Intent(activity, NotesManagerActivity.class);
                activity.startActivityForResult(intent, MainActivity.REQUEST_CODE_FOR_COMING_BACK);
            };
        }

        @Override public Drawable getIcon(Context context) { return null; }
        @Override public boolean hasEvent() { return true; }
        @Override public boolean isSubItem() { return false; }
        @Override public boolean viewIsRecyclable() { return true; }
    }
}
