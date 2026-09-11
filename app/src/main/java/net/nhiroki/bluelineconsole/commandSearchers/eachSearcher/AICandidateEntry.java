package net.nhiroki.bluelineconsole.commandSearchers.eachSearcher;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import androidx.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import net.nhiroki.bluelineconsole.applicationMain.MainActivity;
import net.nhiroki.bluelineconsole.applicationMain.lib.ScreenCaptureHelper;
import net.nhiroki.bluelineconsole.interfaces.CandidateEntry;
import net.nhiroki.bluelineconsole.agent.AgentActionEngine;
import net.nhiroki.bluelineconsole.commands.logs.AppLogger;
import net.nhiroki.bluelineconsole.interfaces.EventLauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AICandidateEntry implements CandidateEntry {
    private static final int STATE_INACTIVE = -1;
    private static final int STATE_CAPTURING = 0;
    private static final int STATE_LOADING = 1;
    private static final int STATE_STREAMING = 2;
    private static final int STATE_SUCCESS = 3;
    private static final int STATE_ERROR = 4;

    private final String mQuestion;
    private final boolean mIsScreenQuery;
    private String mAnswerText = "";
    private int mState = STATE_INACTIVE;
    private Bitmap mCapturedScreenBitmap = null;
    private AgentActionEngine.Action mExtractedAction = null;

    private LinearLayout mView;
    private LinearLayout mHistoryLayout;
    private TextView mHeaderTextView;
    private TextView mContentTextView;
    private ProgressBar mProgressBar;
    private LinearLayout mButtonLayout;
    private TextView mActionButton;

    public AICandidateEntry(String question) {
        this(question, false);
    }

    public AICandidateEntry(String question, boolean isScreenQuery) {
        this.mQuestion = question.trim();
        this.mIsScreenQuery = isScreenQuery;
        if (this.mQuestion.isEmpty() && !this.mIsScreenQuery) {
            this.mState = STATE_SUCCESS;
            this.mAnswerText = "Type '? your question' or 'ai your question' to ask Gemini.\nType '?screen your question' to analyze what is behind this window.";
        }
    }

    @Override
    public String getTitle() {
        if (mIsScreenQuery) {
            return mQuestion.isEmpty() ? "Gemini: Analyze Current Screen" : "Gemini Screen: " + mQuestion;
        }
        return mQuestion.isEmpty() ? "Gemini Assistant" : "Gemini: " + mQuestion;
    }

    @Override
    public View getView(MainActivity mainActivity) {
        if (mView == null) {
            final double pixelsPerSp = mainActivity.getResources().getDisplayMetrics().scaledDensity;

            mView = new LinearLayout(mainActivity);
            mView.setOrientation(LinearLayout.VERTICAL);
            mView.setPadding(0, (int) (6 * pixelsPerSp), 0, (int) (6 * pixelsPerSp));

            TypedValue textColorValue = new TypedValue();
            mainActivity.getTheme().resolveAttribute(net.nhiroki.bluelineconsole.R.attr.bluelineconsoleBaseTextColor, textColorValue, true);
            int baseTextColor = textColorValue.data;

            int accentColor = mainActivity.getAccentColor();

            // Session Header
            mHeaderTextView = new TextView(mainActivity);
            mHeaderTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            mHeaderTextView.setTextColor(accentColor);
            mHeaderTextView.setTypeface(android.graphics.Typeface.MONOSPACE);
            mHeaderTextView.setPadding(0, 0, 0, (int) (4 * pixelsPerSp));
            mView.addView(mHeaderTextView);

            // History conversation view (collapsed bubbles)
            mHistoryLayout = new LinearLayout(mainActivity);
            mHistoryLayout.setOrientation(LinearLayout.VERTICAL);
            mView.addView(mHistoryLayout);

            // Main response/prompt content
            mContentTextView = new TextView(mainActivity);
            mContentTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            mContentTextView.setLineSpacing(0, 1.2f);
            mContentTextView.setTextIsSelectable(true);
            mContentTextView.setTextColor(baseTextColor);
            mView.addView(mContentTextView);

            // Progress spinner
            mProgressBar = new ProgressBar(mainActivity, null, android.R.attr.progressBarStyleSmall);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            progressParams.gravity = Gravity.START;
            progressParams.setMargins(0, (int) (6 * pixelsPerSp), 0, 0);
            mProgressBar.setLayoutParams(progressParams);
            if (Build.VERSION.SDK_INT >= 21) {
                mProgressBar.setIndeterminateTintList(ColorStateList.valueOf(accentColor));
            }
            mView.addView(mProgressBar);

            // Action Buttons (Run Action, Copy, Clear Chat)
            mButtonLayout = new LinearLayout(mainActivity);
            mButtonLayout.setOrientation(LinearLayout.HORIZONTAL);
            mButtonLayout.setPadding(0, (int) (8 * pixelsPerSp), 0, 0);
            mButtonLayout.setVisibility(View.GONE);

            mActionButton = new TextView(mainActivity);
            mActionButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            mActionButton.setTextColor(Color.parseColor("#00f0ff"));
            mActionButton.setTypeface(android.graphics.Typeface.MONOSPACE);
            mActionButton.setPadding(0, (int) (4 * pixelsPerSp), (int) (16 * pixelsPerSp), (int) (4 * pixelsPerSp));
            mActionButton.setVisibility(View.GONE);
            mActionButton.setOnClickListener(v -> {
                if (mExtractedAction != null) {
                    AgentActionEngine.executeAction(mainActivity, mExtractedAction);
                    mainActivity.finishIfNotHome();
                }
            });

            TextView copyButton = new TextView(mainActivity);
            copyButton.setText("[Copy Answer]");
            copyButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            copyButton.setTextColor(accentColor);
            copyButton.setTypeface(android.graphics.Typeface.MONOSPACE);
            copyButton.setPadding(0, (int) (4 * pixelsPerSp), (int) (16 * pixelsPerSp), (int) (4 * pixelsPerSp));
            copyButton.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) mainActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Gemini Answer", mAnswerText);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(mainActivity, "Answer copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
            });

            TextView clearButton = new TextView(mainActivity);
            clearButton.setText("[Clear History]");
            clearButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            clearButton.setTextColor(Color.parseColor("#ff5577"));
            clearButton.setTypeface(android.graphics.Typeface.MONOSPACE);
            clearButton.setPadding(0, (int) (4 * pixelsPerSp), 0, (int) (4 * pixelsPerSp));
            clearButton.setOnClickListener(v -> {
                AIChatSession.getInstance().clear();
                Toast.makeText(mainActivity, "Conversation history cleared.", Toast.LENGTH_SHORT).show();
                renderHistoryViews(mainActivity);
                updateUIState();
            });

            mButtonLayout.addView(mActionButton);
            mButtonLayout.addView(copyButton);
            mButtonLayout.addView(clearButton);
            mView.addView(mButtonLayout);
        }

        renderHistoryViews(mainActivity);
        updateUIState();
        return mView;
    }

    private void renderHistoryViews(MainActivity mainActivity) {
        if (mHistoryLayout == null) return;
        mHistoryLayout.removeAllViews();

        List<AIChatSession.ChatMessage> history = AIChatSession.getInstance().getMessages();
        if (history.isEmpty()) {
            if (mHeaderTextView != null) {
                mHeaderTextView.setText(mIsScreenQuery ? "> GEMINI VISION // SCREEN CONTEXT" : "> GEMINI ASSISTANT");
            }
            return;
        }

        if (mHeaderTextView != null) {
            mHeaderTextView.setText("> GEMINI CHAT // " + history.size() + " MESSAGES IN MEMORY");
        }

        final double pixelsPerSp = mainActivity.getResources().getDisplayMetrics().scaledDensity;

        // Show the last 2 conversation turns in subtle condensed style
        int startIdx = Math.max(0, history.size() - 4);
        for (int i = startIdx; i < history.size(); i++) {
            AIChatSession.ChatMessage msg = history.get(i);
            TextView msgView = new TextView(mainActivity);
            msgView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            msgView.setPadding((int) (6 * pixelsPerSp), (int) (2 * pixelsPerSp), (int) (6 * pixelsPerSp), (int) (2 * pixelsPerSp));

            if ("user".equalsIgnoreCase(msg.role)) {
                int accent = mainActivity.getAccentColor();
                msgView.setTextColor(Color.argb(160, Color.red(accent), Color.green(accent), Color.blue(accent)));
                msgView.setText("[You]: " + msg.text);
            } else {
                msgView.setTextColor(Color.parseColor("#80cccccc"));
                String snippet = msg.text.length() > 90 ? msg.text.substring(0, 90) + "..." : msg.text;
                msgView.setText("[AI]: " + snippet);
            }
            mHistoryLayout.addView(msgView);
        }
    }

    private void updateUIState() {
        if (mContentTextView == null) return;

        if (mState == STATE_INACTIVE) {
            if (mIsScreenQuery) {
                mContentTextView.setText("Press Enter or tap to capture screen & ask Gemini: \"" + (mQuestion.isEmpty() ? "What's on this screen?" : mQuestion) + "\"");
            } else {
                mContentTextView.setText("Press Enter or tap to ask Gemini: \"" + mQuestion + "\"");
            }
            if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
            if (mButtonLayout != null) mButtonLayout.setVisibility(View.GONE);

        } else if (mState == STATE_CAPTURING) {
            mContentTextView.setText("Capturing screen behind launcher...");
            if (mProgressBar != null) mProgressBar.setVisibility(View.VISIBLE);
            if (mButtonLayout != null) mButtonLayout.setVisibility(View.GONE);

        } else if (mState == STATE_LOADING) {
            mContentTextView.setText("Connecting to Gemini...");
            if (mProgressBar != null) mProgressBar.setVisibility(View.VISIBLE);
            if (mButtonLayout != null) mButtonLayout.setVisibility(View.GONE);

        } else if (mState == STATE_STREAMING) {
            String cleanText = mAnswerText.replaceAll("(?i)\\[ACTION:[^\\]]+\\]", "").trim();
            mContentTextView.setText(renderMarkdown(cleanText.isEmpty() ? mAnswerText : cleanText));
            if (mProgressBar != null) mProgressBar.setVisibility(View.VISIBLE);
            if (mButtonLayout != null) mButtonLayout.setVisibility(View.GONE);

        } else if (mState == STATE_SUCCESS) {
            String cleanText = mAnswerText.replaceAll("(?i)\\[ACTION:[^\\]]+\\]", "").trim();
            mContentTextView.setText(renderMarkdown(cleanText.isEmpty() ? mAnswerText : cleanText));
            if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
            if (mButtonLayout != null) mButtonLayout.setVisibility(mAnswerText.isEmpty() ? View.GONE : View.VISIBLE);
            if (mActionButton != null) {
                if (mExtractedAction != null) {
                    String actLabel;
                    if ("SEARCH_APP".equalsIgnoreCase(mExtractedAction.type)) {
                        actLabel = "[▶ Run: Search " + mExtractedAction.appName + " for \"" + mExtractedAction.query + "\"]";
                    } else if ("OPEN_APP".equalsIgnoreCase(mExtractedAction.type)) {
                        actLabel = "[▶ Run: Open " + mExtractedAction.appName + "]";
                    } else if ("SEND_MESSAGE".equalsIgnoreCase(mExtractedAction.type)) {
                        String recip = mExtractedAction.target != null && !mExtractedAction.target.isEmpty() ? mExtractedAction.target : mExtractedAction.appName;
                        actLabel = "[▶ Run: Message " + recip + "]";
                    } else if ("CLICK".equalsIgnoreCase(mExtractedAction.type)) {
                        actLabel = "[▶ Run: Click \"" + mExtractedAction.target + "\"]";
                    } else if ("TYPE".equalsIgnoreCase(mExtractedAction.type)) {
                        actLabel = "[▶ Run: Type \"" + mExtractedAction.query + "\"]";
                    } else if ("OPEN_URL".equalsIgnoreCase(mExtractedAction.type)) {
                        actLabel = "[▶ Run: Open URL]";
                    } else {
                        actLabel = "[▶ Run Action]";
                    }
                    mActionButton.setText(actLabel);
                    mActionButton.setVisibility(View.VISIBLE);
                } else {
                    mActionButton.setVisibility(View.GONE);
                }
            }

        } else if (mState == STATE_ERROR) {
            mContentTextView.setText(renderMarkdown(mAnswerText));
            if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
            if (mButtonLayout != null) mButtonLayout.setVisibility(View.GONE);
        }
    }

    private void streamAIResponse(final String apiKey, final String model, final String imageBase64, final MainActivity activity) {
        new Thread(() -> {
            OutputStream os = null;
            InputStream is = null;
            BufferedReader reader = null;
            HttpURLConnection conn = null;
            StringBuilder fullAnswer = new StringBuilder();

            try {
                // Gemini SSE streaming endpoint
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setDoOutput(true);
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(30000);

                // Build full conversation contents payload
                JSONArray contentsArray = new JSONArray();

                // 1. Add historical conversation turns
                List<AIChatSession.ChatMessage> history = AIChatSession.getInstance().getMessages();
                for (AIChatSession.ChatMessage historyMsg : history) {
                    JSONObject histObj = new JSONObject();
                    histObj.put("role", historyMsg.role);
                    JSONArray histParts = new JSONArray();
                    histParts.put(new JSONObject().put("text", historyMsg.text));
                    if (historyMsg.imageBase64 != null) {
                        JSONObject imgObj = new JSONObject();
                        imgObj.put("mime_type", "image/jpeg");
                        imgObj.put("data", historyMsg.imageBase64);
                        histParts.put(new JSONObject().put("inline_data", imgObj));
                    }
                    histObj.put("parts", histParts);
                    contentsArray.put(histObj);
                }

                // 2. Add current turn
                JSONObject currentTurn = new JSONObject();
                currentTurn.put("role", "user");
                JSONArray currentParts = new JSONArray();

                String prompt = mQuestion.isEmpty() ? "Analyze this screen and explain or summarize what is displayed." : mQuestion;
                AppLogger.i("AI", "Sending request to Gemini (model=" + model + ", prompt='" + prompt + "', hasImage=" + (imageBase64 != null) + ")");
                currentParts.put(new JSONObject().put("text", prompt));

                if (imageBase64 != null) {
                    JSONObject imgObj = new JSONObject();
                    imgObj.put("mime_type", "image/jpeg");
                    imgObj.put("data", imageBase64);
                    currentParts.put(new JSONObject().put("inline_data", imgObj));
                }
                currentTurn.put("parts", currentParts);
                contentsArray.put(currentTurn);

                JSONObject payload = new JSONObject();
                payload.put("contents", contentsArray);

                JSONObject systemInstruction = new JSONObject();
                JSONArray sysParts = new JSONArray();
                sysParts.put(new JSONObject().put("text",
                        "You are BlueLine Agent, an intelligent Android device assistant. When the user asks you to perform an action on their device (open an app, search inside an app, send a message, click a button, open a URL, type text), answer briefly and append an action tag at the end in one of these formats:\n" +
                        "[ACTION: OPEN_APP, <appName>]\n" +
                        "[ACTION: SEARCH_APP, <appName>, <searchQuery>]\n" +
                        "[ACTION: SEND_MESSAGE, <appName>, <recipient>, <message>]\n" +
                        "[ACTION: OPEN_URL, <url>]\n" +
                        "[ACTION: CLICK, <buttonOrText>]\n" +
                        "[ACTION: TYPE, <text>]\n" +
                        "If the user is asking a standard informational question, answer normally without any [ACTION: ...] tag."));
                systemInstruction.put("parts", sysParts);
                payload.put("system_instruction", systemInstruction);

                byte[] input = payload.toString().getBytes("utf-8");
                os = conn.getOutputStream();
                os.write(input, 0, input.length);

                int code = conn.getResponseCode();
                AppLogger.i("AI", "Gemini HTTP response code: " + code);
                if (code == 200) {
                    is = conn.getInputStream();
                    reader = new BufferedReader(new InputStreamReader(is, "utf-8"));
                    String line;

                    activity.runOnUiThread(() -> {
                        mState = STATE_STREAMING;
                        updateUIState();
                    });

                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String jsonStr = line.substring(6).trim();
                            if (!jsonStr.isEmpty() && !jsonStr.equals("[DONE]")) {
                                try {
                                    JSONObject chunk = new JSONObject(jsonStr);
                                    JSONArray candidates = chunk.optJSONArray("candidates");
                                    if (candidates != null && candidates.length() > 0) {
                                        JSONObject c = candidates.getJSONObject(0);
                                        JSONObject content = c.optJSONObject("content");
                                        if (content != null) {
                                            JSONArray parts = content.optJSONArray("parts");
                                            if (parts != null && parts.length() > 0) {
                                                String delta = parts.getJSONObject(0).optString("text", "");
                                                if (!delta.isEmpty()) {
                                                    fullAnswer.append(delta);
                                                    mAnswerText = fullAnswer.toString();
                                                    activity.runOnUiThread(this::updateUIState);
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    mAnswerText = fullAnswer.toString().trim();
                    AppLogger.i("AI", "Gemini response completed (length=" + mAnswerText.length() + ")");
                    AppLogger.d("AI", "Gemini text: " + mAnswerText);
                    mExtractedAction = parseActionFromResponse(mAnswerText);
                    if (mExtractedAction != null) {
                        AppLogger.i("AGENT", "Parsed action from AI: " + mExtractedAction.type + " (app=" + mExtractedAction.appName + ", target=" + mExtractedAction.target + ", query=" + mExtractedAction.query + ")");
                    } else {
                        AppLogger.d("AGENT", "No action tag found in Gemini response.");
                    }
                    mState = STATE_SUCCESS;

                    // Record both user question and model answer into AIChatSession
                    AIChatSession.getInstance().addMessage(new AIChatSession.ChatMessage("user", prompt, imageBase64));
                    AIChatSession.getInstance().addMessage(new AIChatSession.ChatMessage("model", mAnswerText));

                } else {
                    String detail = "";
                    try {
                        InputStream es = conn.getErrorStream();
                        if (es != null) {
                            BufferedReader errReader = new BufferedReader(new InputStreamReader(es, "utf-8"));
                            StringBuilder errSb = new StringBuilder();
                            String l;
                            while ((l = errReader.readLine()) != null) {
                                errSb.append(l);
                            }
                            errReader.close();
                            JSONObject errJson = new JSONObject(errSb.toString());
                            if (errJson.has("error") && errJson.getJSONObject("error").has("message")) {
                                detail = ": " + errJson.getJSONObject("error").getString("message");
                            }
                        }
                    } catch (Exception ignored) {}
                    AppLogger.e("AI", "Gemini API error HTTP " + code + detail);
                    mAnswerText = "Error: API returned HTTP " + code + detail + "\n(Tap to retry)";
                    mState = STATE_ERROR;
                }
            } catch (Exception e) {
                AppLogger.e("AI", "Gemini request exception: " + e.getMessage(), e);
                mAnswerText = "Error: " + e.getMessage() + "\n(Tap to retry)";
                mState = STATE_ERROR;
            } finally {
                try { if (os != null) os.close(); } catch (Exception ignored) {}
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }

            activity.runOnUiThread(() -> {
                renderHistoryViews(activity);
                updateUIState();
            });
        }).start();
    }

    private void proceedWithAIRequest(final MainActivity activity) {
        final String apiKey = PreferenceManager.getDefaultSharedPreferences(activity).getString("pref_ai_api_key", "").trim();
        String rawModel = PreferenceManager.getDefaultSharedPreferences(activity).getString("pref_ai_model", "gemini-2.5-flash").trim();
        if ("custom".equalsIgnoreCase(rawModel)) {
            String custom = PreferenceManager.getDefaultSharedPreferences(activity).getString("pref_ai_custom_model", "").trim();
            if (!custom.isEmpty()) {
                rawModel = custom;
            }
        }
        final String model = rawModel.isEmpty() ? "gemini-2.5-flash" : rawModel;

        if (apiKey.isEmpty()) {
            mState = STATE_ERROR;
            mAnswerText = "Error: Please set your Gemini API key in Settings.\n(Tap to retry)";
            updateUIState();
            return;
        }

        if (mIsScreenQuery) {
            if (!ScreenCaptureHelper.hasPermission()) {
                mState = STATE_INACTIVE;
                Toast.makeText(activity, "Requesting screen capture permission...", Toast.LENGTH_SHORT).show();
                activity.requestScreenCapture(() -> proceedWithAIRequest(activity));
                return;
            }

            mState = STATE_CAPTURING;
            updateUIState();

            ScreenCaptureHelper.captureScreenBehindActivity(activity, new ScreenCaptureHelper.CaptureCallback() {
                @Override
                public void onScreenCaptured(Bitmap bitmap) {
                    mCapturedScreenBitmap = bitmap;
                    String base64Jpeg = ScreenCaptureHelper.bitmapToBase64Jpeg(bitmap, 1280, 80);
                    mState = STATE_LOADING;
                    updateUIState();
                    streamAIResponse(apiKey, model, base64Jpeg, activity);
                }

                @Override
                public void onError(String message) {
                    mState = STATE_ERROR;
                    mAnswerText = "Screen capture failed: " + message + "\n(Tap to retry)";
                    updateUIState();
                }
            });
        } else {
            mState = STATE_LOADING;
            updateUIState();
            streamAIResponse(apiKey, model, null, activity);
        }
    }

    @Override
    public boolean hasLongView() {
        return true;
    }

    @Override
    public EventLauncher getEventLauncher(Context context) {
        return new EventLauncher() {
            @Override
            public void launch(MainActivity activity) {
                if (mState == STATE_INACTIVE || mState == STATE_ERROR) {
                    proceedWithAIRequest(activity);
                } else if (mState == STATE_SUCCESS) {
                    if (mExtractedAction != null) {
                        AgentActionEngine.executeAction(activity, mExtractedAction);
                        activity.finishIfNotHome();
                    } else if (!mAnswerText.isEmpty()) {
                        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Gemini Answer", mAnswerText);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(activity, "Answer copied to clipboard!", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        };
    }

    public static AgentActionEngine.Action parseActionFromResponse(String text) {
        if (text == null) return null;
        Pattern p = Pattern.compile("\\[ACTION:\\s*([A-Z_]+)(?:,\\s*([^,\\]]+))?(?:,\\s*([^,\\]]+))?(?:,\\s*([^\\]]+))?\\]", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String type = m.group(1).trim().toUpperCase();
            String p1 = m.group(2) != null ? m.group(2).trim() : null;
            String p2 = m.group(3) != null ? m.group(3).trim() : null;
            String p3 = m.group(4) != null ? m.group(4).trim() : null;

            if (p1 != null) {
                p1 = p1.replaceAll("^[\"']+|[\"']+$", "").trim();
            }
            if (p2 != null) {
                p2 = p2.replaceAll("^[\"']+|[\"']+$", "").trim();
            }
            if (p3 != null) {
                p3 = p3.replaceAll("^[\"']+|[\"']+$", "").trim();
            }

            if ("SEND_MESSAGE".equals(type)) {
                if (p3 != null) {
                    return new AgentActionEngine.Action("SEND_MESSAGE", p1, p2, p3);
                } else if (p2 != null) {
                    AgentActionEngine.MessageDetails details = AgentActionEngine.parseMessageDetails(p2);
                    return new AgentActionEngine.Action("SEND_MESSAGE", p1, details.recipient, details.message);
                } else {
                    return new AgentActionEngine.Action("OPEN_APP", p1, null);
                }
            } else if ("SEARCH_APP".equals(type) || "SEARCH".equals(type)) {
                return new AgentActionEngine.Action("SEARCH_APP", p1, p2 != null ? p2 : "");
            } else if ("OPEN_APP".equals(type)) {
                return new AgentActionEngine.Action("OPEN_APP", p1, null);
            } else if ("CLICK".equals(type)) {
                return new AgentActionEngine.Action("CLICK", p1);
            } else if ("TYPE".equals(type)) {
                return new AgentActionEngine.Action("TYPE", null, p1);
            } else if ("OPEN_URL".equals(type)) {
                return new AgentActionEngine.Action("OPEN_URL", p1);
            }
        }
        return null;
    }

    @Override
    public Drawable getIcon(Context context) {
        return null;
    }

    @Override
    public boolean hasEvent() {
        return true;
    }

    @Override
    public boolean isSubItem() {
        return false;
    }

    @Override
    public boolean viewIsRecyclable() {
        return false;
    }

    private android.text.Spanned renderMarkdown(String markdown) {
        if (markdown == null) {
            return new android.text.SpannableString("");
        }

        // 1. Escape HTML special characters
        String html = markdown.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        // 2. Format code blocks (```code```)
        html = html.replaceAll("(?s)```(.*?)```", "<pre><tt>$1</tt></pre>");

        // 3. Replace bullet points
        html = html.replaceAll("(?m)^[\\*\\-]\\s+(.*?)$", "&#8226; $1");

        // 4. Headers
        html = html.replaceAll("(?m)^###\\s+(.*?)$", "<b>$1</b>");
        html = html.replaceAll("(?m)^##\\s+(.*?)$", "<b><big>$1</big></b>");
        html = html.replaceAll("(?m)^#\\s+(.*?)$", "<b><big><big>$1</big></big></b>");

        // 5. Bold & Italics
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        html = html.replaceAll("(?<!\\w)\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\w)", "<i>$1</i>");

        // 6. Inline code
        html = html.replaceAll("`(.+?)`", "<tt style=\"color:#00f0ff;\">$1</tt>");

        // 7. Newlines
        html = html.replaceAll("\n", "<br/>");

        if (android.os.Build.VERSION.SDK_INT >= 24) {
            return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY);
        } else {
            return android.text.Html.fromHtml(html);
        }
    }
}
