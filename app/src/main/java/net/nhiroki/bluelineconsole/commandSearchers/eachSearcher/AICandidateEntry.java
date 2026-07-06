package net.nhiroki.bluelineconsole.commandSearchers.eachSearcher;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import androidx.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import net.nhiroki.bluelineconsole.applicationMain.MainActivity;
import net.nhiroki.bluelineconsole.interfaces.CandidateEntry;
import net.nhiroki.bluelineconsole.interfaces.EventLauncher;

public class AICandidateEntry implements CandidateEntry {
    private static final int STATE_LOADING = 0;
    private static final int STATE_SUCCESS = 1;
    private static final int STATE_ERROR = 2;

    private final String mQuestion;
    private String mAnswerText = "";
    private int mState = STATE_LOADING;
    private boolean mRequestStarted = false;

    private LinearLayout mView;
    private TextView mContentTextView;
    private ProgressBar mProgressBar;

    public AICandidateEntry(String question) {
        this.mQuestion = question;
    }

    @Override
    public String getTitle() {
        return mQuestion.isEmpty() ? "Gemini Assistant" : "Gemini: " + mQuestion;
    }

    @Override
    public View getView(MainActivity mainActivity) {
        if (mView == null) {
            final double pixelsPerSp = mainActivity.getResources().getDisplayMetrics().scaledDensity;

            mView = new LinearLayout(mainActivity);
            mView.setOrientation(LinearLayout.VERTICAL);
            mView.setPadding(0, (int)(8 * pixelsPerSp), 0, (int)(8 * pixelsPerSp));

            mContentTextView = new TextView(mainActivity);
            mContentTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            mContentTextView.setLineSpacing(0, 1.2f);
            mContentTextView.setTextIsSelectable(true);

            // Fetch text color attribute from active theme
            TypedValue textColorValue = new TypedValue();
            mainActivity.getTheme().resolveAttribute(net.nhiroki.bluelineconsole.R.attr.bluelineconsoleBaseTextColor, textColorValue, true);
            mContentTextView.setTextColor(textColorValue.data);

            mProgressBar = new ProgressBar(mainActivity, null, android.R.attr.progressBarStyleSmall);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            progressParams.gravity = Gravity.START;
            progressParams.setMargins(0, (int)(8 * pixelsPerSp), 0, 0);
            mProgressBar.setLayoutParams(progressParams);

            mView.addView(mContentTextView);
            mView.addView(mProgressBar);
        }

        if (mState == STATE_LOADING) {
            mContentTextView.setText("Thinking...");
            mProgressBar.setVisibility(View.VISIBLE);
        } else {
            mContentTextView.setText(mAnswerText);
            mProgressBar.setVisibility(View.GONE);
        }

        if (!mRequestStarted && !mQuestion.isEmpty()) {
            mRequestStarted = true;
            String apiKey = PreferenceManager.getDefaultSharedPreferences(mainActivity).getString("pref_ai_api_key", "").trim();
            String model = PreferenceManager.getDefaultSharedPreferences(mainActivity).getString("pref_ai_model", "gemini-2.5-flash").trim();
            if (model.isEmpty()) {
                model = "gemini-2.5-flash";
            }
            if (apiKey.isEmpty()) {
                mState = STATE_ERROR;
                mAnswerText = "Error: Please set your Gemini API key in Settings.";
                updateUI();
            } else {
                fetchAIAnswer(apiKey, model, mainActivity);
            }
        } else if (mQuestion.isEmpty()) {
            mState = STATE_SUCCESS;
            mAnswerText = "Type '? your question' or 'ai your question' to ask Gemini.";
            updateUI();
        }

        return mView;
    }

    private void updateUI() {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.GONE);
        }
        if (mContentTextView != null) {
            mContentTextView.setText(mAnswerText);
        }
    }

    private void fetchAIAnswer(final String apiKey, final String model, final MainActivity activity) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                java.io.OutputStream os = null;
                java.io.InputStream is = null;
                java.io.BufferedReader reader = null;
                java.net.HttpURLConnection conn = null;
                try {
                    java.net.URL url = new java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey);
                    conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; utf-8");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);

                    // JSON request payload
                    org.json.JSONObject part = new org.json.JSONObject().put("text", mQuestion);
                    org.json.JSONArray parts = new org.json.JSONArray().put(part);
                    org.json.JSONObject content = new org.json.JSONObject().put("parts", parts);
                    org.json.JSONArray contents = new org.json.JSONArray().put(content);
                    org.json.JSONObject payload = new org.json.JSONObject().put("contents", contents);

                    byte[] input = payload.toString().getBytes("utf-8");
                    os = conn.getOutputStream();
                    os.write(input, 0, input.length);

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        is = conn.getInputStream();
                        reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "utf-8"));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line.trim());
                        }

                        org.json.JSONObject jsonResponse = new org.json.JSONObject(response.toString());
                        String answer = jsonResponse.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        mAnswerText = answer.trim();
                        mState = STATE_SUCCESS;
                    } else {
                        mAnswerText = "Error: API returned HTTP code " + code;
                        mState = STATE_ERROR;
                    }
                } catch (Exception e) {
                    mAnswerText = "Error: " + e.getMessage();
                    mState = STATE_ERROR;
                } finally {
                    try { if (os != null) os.close(); } catch (Exception ignored) {}
                    try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                    try { if (is != null) is.close(); } catch (Exception ignored) {}
                    if (conn != null) conn.disconnect();
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateUI();
                    }
                });
            }
        }).start();
    }

    @Override
    public boolean hasLongView() {
        return true;
    }

    @Override
    public EventLauncher getEventLauncher(Context context) {
        if (mState == STATE_SUCCESS && !mAnswerText.isEmpty()) {
            return new EventLauncher() {
                @Override
                public void launch(MainActivity activity) {
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Gemini Answer", mAnswerText);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(activity, "Answer copied to clipboard!", Toast.LENGTH_SHORT).show();
                    }
                }
            };
        }
        return null;
    }

    @Override
    public Drawable getIcon(Context context) {
        return null;
    }

    @Override
    public boolean hasEvent() {
        return mState == STATE_SUCCESS && !mAnswerText.isEmpty();
    }

    @Override
    public boolean isSubItem() {
        return false;
    }

    @Override
    public boolean viewIsRecyclable() {
        return false;
    }
}
