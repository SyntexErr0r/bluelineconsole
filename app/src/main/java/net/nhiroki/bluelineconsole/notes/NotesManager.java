package net.nhiroki.bluelineconsole.notes;

import android.content.Context;
import android.content.SharedPreferences;

import net.nhiroki.bluelineconsole.commands.logs.AppLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotesManager {
    private static final String TAG = "NOTES";
    private static final String PREF_FILE = "pref_blueline_notes";
    private static final String KEY_NOTES_JSON = "key_saved_notes_json";

    private static volatile NotesManager sInstance;

    private final List<Note> mNotes = new ArrayList<>();
    private boolean mInitialized = false;

    private NotesManager() {}

    public static NotesManager getInstance() {
        if (sInstance == null) {
            synchronized (NotesManager.class) {
                if (sInstance == null) {
                    sInstance = new NotesManager();
                }
            }
        }
        return sInstance;
    }

    public synchronized void ensureInitialized(Context context) {
        if (mInitialized || context == null) return;
        mNotes.clear();
        try {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_NOTES_JSON, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    Note n = Note.fromJson(obj);
                    if (n != null) {
                        mNotes.add(n);
                    }
                }
            }
            sortNotes();
            AppLogger.i(TAG, "Loaded " + mNotes.size() + " notes from persistent storage");
        } catch (Exception e) {
            AppLogger.e(TAG, "Error loading notes", e);
        } finally {
            mInitialized = true;
        }
    }

    private synchronized void saveNotes(Context context) {
        if (context == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (Note n : mNotes) {
                arr.put(n.toJson());
            }
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_NOTES_JSON, arr.toString()).apply();
        } catch (Exception e) {
            AppLogger.e(TAG, "Error saving notes", e);
        }
    }

    private void sortNotes() {
        Collections.sort(mNotes, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
    }

    public synchronized List<Note> getAllNotes(Context context) {
        ensureInitialized(context);
        return new ArrayList<>(mNotes);
    }

    public synchronized Note getNoteById(Context context, String id) {
        if (id == null) return null;
        ensureInitialized(context);
        for (Note n : mNotes) {
            if (id.equals(n.id)) {
                return n;
            }
        }
        return null;
    }

    public synchronized void saveNote(Context context, Note note) {
        if (note == null) return;
        ensureInitialized(context);
        note.updatedAt = System.currentTimeMillis();

        int existingIndex = -1;
        for (int i = 0; i < mNotes.size(); i++) {
            if (mNotes.get(i).id.equals(note.id)) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex != -1) {
            mNotes.set(existingIndex, note);
        } else {
            mNotes.add(0, note);
        }

        sortNotes();
        saveNotes(context);
        AppLogger.i(TAG, "Saved note: id=" + note.id + ", title='" + note.title + "'");
    }

    public synchronized boolean deleteNote(Context context, String id) {
        if (id == null) return false;
        ensureInitialized(context);
        boolean removed = false;
        for (int i = 0; i < mNotes.size(); i++) {
            if (mNotes.get(i).id.equals(id)) {
                mNotes.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            saveNotes(context);
            AppLogger.i(TAG, "Deleted note: id=" + id);
        }
        return removed;
    }

    public synchronized List<Note> searchNotes(Context context, String query) {
        ensureInitialized(context);
        List<Note> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(mNotes);
        }
        String q = query.trim().toLowerCase();
        for (Note n : mNotes) {
            boolean titleMatch = n.title != null && n.title.toLowerCase().contains(q);
            boolean contentMatch = n.content != null && n.content.toLowerCase().contains(q);
            if (titleMatch || contentMatch) {
                results.add(n);
            }
        }
        return results;
    }
}
