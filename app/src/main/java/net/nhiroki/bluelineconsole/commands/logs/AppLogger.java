package net.nhiroki.bluelineconsole.commands.logs;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLogger {
    private static final int MAX_ENTRIES = 1000;
    private static final List<LogEntry> sEntries = new ArrayList<>();
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    public static class LogEntry {
        public final long timestamp;
        public final String timeStr;
        public final String level;
        public final String tag;
        public final String message;
        public final String errorDetails;

        public LogEntry(long timestamp, String level, String tag, String message, String errorDetails) {
            this.timestamp = timestamp;
            this.timeStr = TIME_FORMAT.format(new Date(timestamp));
            this.level = level;
            this.tag = tag;
            this.message = message;
            this.errorDetails = errorDetails;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(timeStr).append("] [").append(level).append("/").append(tag).append("] ").append(message);
            if (errorDetails != null && !errorDetails.isEmpty()) {
                sb.append("\n").append(errorDetails);
            }
            return sb.toString();
        }
    }

    private static synchronized void addEntry(String level, String tag, String message, String errorDetails) {
        if (sEntries.size() >= MAX_ENTRIES) {
            sEntries.remove(0);
        }
        sEntries.add(new LogEntry(System.currentTimeMillis(), level, tag, message, errorDetails));
    }

    public static synchronized void d(String tag, String message) {
        Log.d("BLC/" + tag, message);
        addEntry("D", tag, message, null);
    }

    public static synchronized void i(String tag, String message) {
        Log.i("BLC/" + tag, message);
        addEntry("I", tag, message, null);
    }

    public static synchronized void w(String tag, String message) {
        Log.w("BLC/" + tag, message);
        addEntry("W", tag, message, null);
    }

    public static synchronized void e(String tag, String message) {
        Log.e("BLC/" + tag, message);
        addEntry("E", tag, message, null);
    }

    public static synchronized void e(String tag, String message, Throwable tr) {
        String details = null;
        if (tr != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            tr.printStackTrace(pw);
            details = sw.toString();
        }
        Log.e("BLC/" + tag, message, tr);
        addEntry("E", tag, message, details);
    }

    public static synchronized List<LogEntry> getEntries() {
        return new ArrayList<>(sEntries);
    }

    public static synchronized int getCount() {
        return sEntries.size();
    }

    public static synchronized void clear() {
        sEntries.clear();
        i("LOG", "Log buffer cleared by user.");
    }

    public static synchronized String getAllLogsAsString() {
        if (sEntries.isEmpty()) {
            return "--- No logs recorded yet ---";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== BlueLine Console & Agent Logs (Total: ").append(sEntries.size()).append(") ===\n\n");
        for (LogEntry entry : sEntries) {
            sb.append(entry.toString()).append("\n");
        }
        return sb.toString();
    }

    public static synchronized String getRecentLogsSummary(int count) {
        if (sEntries.isEmpty()) {
            return "No logs recorded yet.";
        }
        int start = Math.max(0, sEntries.size() - count);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < sEntries.size(); i++) {
            sb.append(sEntries.get(i).toString()).append("\n");
        }
        return sb.toString().trim();
    }

    public static String getSystemLogcat(int maxLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== System Logcat (Recent ").append(maxLines).append(" lines) ===\n\n");
        Process process = null;
        BufferedReader reader = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-t", String.valueOf(maxLines)});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            sb.append("Failed to retrieve logcat: ").append(e.getMessage()).append("\n");
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (process != null) {
                process.destroy();
            }
        }
        return sb.toString();
    }
}
