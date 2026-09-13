package net.nhiroki.bluelineconsole.notes;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class Note {
    public String id;
    public String title;
    public String content;
    public long createdAt;
    public long updatedAt;

    public Note() {
        this.id = UUID.randomUUID().toString();
        this.title = "";
        this.content = "";
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Note(String title, String content) {
        this.id = UUID.randomUUID().toString();
        this.title = title != null ? title : "";
        this.content = content != null ? content : "";
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("title", title);
        obj.put("content", content);
        obj.put("createdAt", createdAt);
        obj.put("updatedAt", updatedAt);
        return obj;
    }

    public static Note fromJson(JSONObject obj) {
        if (obj == null) return null;
        Note n = new Note();
        n.id = obj.optString("id", UUID.randomUUID().toString());
        n.title = obj.optString("title", "");
        n.content = obj.optString("content", "");
        n.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
        n.updatedAt = obj.optLong("updatedAt", n.createdAt);
        return n;
    }
}
