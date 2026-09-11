package net.nhiroki.bluelineconsole.commandSearchers.eachSearcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AIChatSession {
    public static class ChatMessage {
        public final String role; // "user" or "model"
        public final String text;
        public final String imageBase64; // nullable, for screen capture multimodal query

        public ChatMessage(String role, String text) {
            this(role, text, null);
        }

        public ChatMessage(String role, String text, String imageBase64) {
            this.role = role;
            this.text = text;
            this.imageBase64 = imageBase64;
        }
    }

    private static AIChatSession sInstance;
    private final List<ChatMessage> mMessages = new ArrayList<>();

    private AIChatSession() {}

    public static synchronized AIChatSession getInstance() {
        if (sInstance == null) {
            sInstance = new AIChatSession();
        }
        return sInstance;
    }

    public synchronized void addMessage(ChatMessage msg) {
        if (msg != null && msg.text != null && !msg.text.trim().isEmpty()) {
            mMessages.add(msg);
            // Cap history to last 20 messages to prevent unbounded memory growth and payload size
            if (mMessages.size() > 20) {
                mMessages.remove(0);
            }
        }
    }

    public synchronized List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(mMessages));
    }

    public synchronized void clear() {
        mMessages.clear();
    }

    public synchronized boolean isEmpty() {
        return mMessages.isEmpty();
    }

    public synchronized int size() {
        return mMessages.size();
    }
}
