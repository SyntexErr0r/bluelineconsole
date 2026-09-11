package net.nhiroki.bluelineconsole.commandSearchers.eachSearcher;

import android.content.ClipData;
import android.content.ClipboardManager;
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
import net.nhiroki.bluelineconsole.commands.logs.AppLogger;
import net.nhiroki.bluelineconsole.commands.logs.LogViewerActivity;
import net.nhiroki.bluelineconsole.interfaces.CandidateEntry;
import net.nhiroki.bluelineconsole.interfaces.CommandSearcher;
import net.nhiroki.bluelineconsole.interfaces.EventLauncher;

import java.util.ArrayList;
import java.util.List;

public class LogCommandSearcher implements CommandSearcher {

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

        String q = query.trim().toLowerCase();
        if (q.equals("log") || q.equals("logs") || q.equals("agentlog") || q.equals("debug") ||
            q.startsWith("log ") || q.startsWith("logs ") || q.equals("logcat")) {

            boolean isLogcatQuery = q.contains("cat") || q.contains("sys");

            if (isLogcatQuery) {
                candidates.add(new LogcatCandidateEntry());
                candidates.add(new LogViewerCandidateEntry());
            } else {
                candidates.add(new LogViewerCandidateEntry());
                candidates.add(new LogCopyCandidateEntry());
                candidates.add(new LogClearCandidateEntry());
                candidates.add(new LogcatCandidateEntry());
            }
        }

        return candidates;
    }

    public static class LogViewerCandidateEntry implements CandidateEntry {
        @NonNull
        @Override
        public String getTitle() {
            return "📋 View Console & Agent Logs";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView countTv = new TextView(mainActivity);
            int count = AppLogger.getCount();
            countTv.setText(count + " events recorded. Tap or press Enter to open full viewer.");
            countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            countTv.setTextColor(mainActivity.getAccentColor());
            countTv.setTypeface(Typeface.MONOSPACE);
            layout.addView(countTv);

            String recent = AppLogger.getRecentLogsSummary(2);
            if (!recent.isEmpty() && !recent.equals("No logs recorded yet.")) {
                TextView previewTv = new TextView(mainActivity);
                previewTv.setText(recent);
                previewTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                previewTv.setTextColor(Color.parseColor("#888888"));
                previewTv.setTypeface(Typeface.MONOSPACE);
                previewTv.setPadding(0, 4, 0, 0);
                layout.addView(previewTv);
            }

            return layout;
        }

        @Override
        public boolean hasLongView() {
            return true;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                Intent intent = new Intent(activity, LogViewerActivity.class);
                intent.putExtra(LogViewerActivity.EXTRA_MODE, LogViewerActivity.MODE_APP);
                activity.startActivityForResult(intent, MainActivity.REQUEST_CODE_FOR_COMING_BACK);
            };
        }

        @Override public Drawable getIcon(Context context) { return null; }
        @Override public boolean hasEvent() { return true; }
        @Override public boolean isSubItem() { return false; }
        @Override public boolean viewIsRecyclable() { return true; }
    }

    public static class LogCopyCandidateEntry implements CandidateEntry {
        @NonNull
        @Override
        public String getTitle() {
            return "📋 Copy All Logs to Clipboard";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            TextView tv = new TextView(mainActivity);
            tv.setText("Copy entire log buffer to Android clipboard");
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTextColor(Color.parseColor("#00f0ff"));
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setPadding(0, 4, 0, 8);
            return tv;
        }

        @Override
        public boolean hasLongView() {
            return false;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("BlueLine Logs", AppLogger.getAllLogsAsString());
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(activity, "All logs copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
                activity.finishIfNotHome();
            };
        }

        @Override public Drawable getIcon(Context context) { return null; }
        @Override public boolean hasEvent() { return true; }
        @Override public boolean isSubItem() { return true; }
        @Override public boolean viewIsRecyclable() { return true; }
    }

    public static class LogClearCandidateEntry implements CandidateEntry {
        @NonNull
        @Override
        public String getTitle() {
            return "🗑️ Clear Log Buffer";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            TextView tv = new TextView(mainActivity);
            tv.setText("Erase all in-memory recorded logs");
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTextColor(Color.parseColor("#ff5577"));
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setPadding(0, 4, 0, 8);
            return tv;
        }

        @Override
        public boolean hasLongView() {
            return false;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLogger.clear();
                Toast.makeText(activity, "Log buffer cleared.", Toast.LENGTH_SHORT).show();
                activity.changeInputText("");
            };
        }

        @Override public Drawable getIcon(Context context) { return null; }
        @Override public boolean hasEvent() { return true; }
        @Override public boolean isSubItem() { return true; }
        @Override public boolean viewIsRecyclable() { return true; }
    }

    public static class LogcatCandidateEntry implements CandidateEntry {
        @NonNull
        @Override
        public String getTitle() {
            return "📱 View System Logcat";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            TextView tv = new TextView(mainActivity);
            tv.setText("Read Android OS logcat messages");
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTextColor(Color.parseColor("#ffaa00"));
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setPadding(0, 4, 0, 8);
            return tv;
        }

        @Override
        public boolean hasLongView() {
            return false;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                Intent intent = new Intent(activity, LogViewerActivity.class);
                intent.putExtra(LogViewerActivity.EXTRA_MODE, LogViewerActivity.MODE_LOGCAT);
                activity.startActivityForResult(intent, MainActivity.REQUEST_CODE_FOR_COMING_BACK);
            };
        }

        @Override public Drawable getIcon(Context context) { return null; }
        @Override public boolean hasEvent() { return true; }
        @Override public boolean isSubItem() { return true; }
        @Override public boolean viewIsRecyclable() { return true; }
    }
}
