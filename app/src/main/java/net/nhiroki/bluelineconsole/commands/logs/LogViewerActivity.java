package net.nhiroki.bluelineconsole.commands.logs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.BaseWindowActivity;

import java.util.concurrent.Executors;

public class LogViewerActivity extends BaseWindowActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_LOGCAT = "logcat";
    public static final String MODE_APP = "app";

    private boolean mIsLogcatMode = false;
    private TextView mTitleView;
    private TextView mSubtitleView;
    private TextView mContentView;
    private TextView mBtnToggle;
    private ScrollView mScrollView;

    public LogViewerActivity() {
        super(R.layout.activity_log_viewer, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setHeaderFooterTexts("LOGS", null);
        this.setWindowBoundarySize(ROOT_WINDOW_FULL_WIDTH_ALWAYS, 1);
        this.changeBaseWindowElementSizeForAnimation(false);
        this.enableBaseWindowAnimation();

        mTitleView = findViewById(R.id.log_viewer_title);
        mSubtitleView = findViewById(R.id.log_viewer_subtitle);
        mContentView = findViewById(R.id.log_viewer_content);
        mBtnToggle = findViewById(R.id.btn_toggle_mode);
        mScrollView = findViewById(R.id.log_viewer_scroll);

        String initialMode = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_LOGCAT.equalsIgnoreCase(initialMode)) {
            mIsLogcatMode = true;
        }

        findViewById(R.id.btn_copy_logs).setOnClickListener(v -> {
            CharSequence text = mContentView.getText();
            if (text != null && text.length() > 0) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("BlueLine Logs", text);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.btn_refresh_logs).setOnClickListener(v -> loadLogs());

        mBtnToggle.setOnClickListener(v -> {
            mIsLogcatMode = !mIsLogcatMode;
            loadLogs();
        });

        findViewById(R.id.btn_clear_logs).setOnClickListener(v -> {
            if (mIsLogcatMode) {
                Toast.makeText(this, "System logcat cannot be cleared by user apps.", Toast.LENGTH_SHORT).show();
            } else {
                AppLogger.clear();
                loadLogs();
                Toast.makeText(this, "App log buffer cleared.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLogs();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        this.changeBaseWindowElementSizeForAnimation(true);
    }

    private void loadLogs() {
        if (mIsLogcatMode) {
            mTitleView.setText("[SYSTEM LOGCAT]");
            mSubtitleView.setText("Fetching system logcat...");
            mBtnToggle.setText("[VIEW: APP]");
            mContentView.setText("Loading logcat...");

            Executors.newSingleThreadExecutor().execute(() -> {
                final String logcat = AppLogger.getSystemLogcat(250);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isFinishing()) {
                        mSubtitleView.setText("Recent 250 logcat lines. Tap [COPY] to export.");
                        mContentView.setText(logcat);
                        scrollToBottom();
                    }
                });
            });
        } else {
            mTitleView.setText("[CONSOLE & AGENT LOGS]");
            int count = AppLogger.getCount();
            mSubtitleView.setText(count + " events recorded in current session. Tap [COPY] to export.");
            mBtnToggle.setText("[VIEW: LOGCAT]");
            mContentView.setText(AppLogger.getAllLogsAsString());
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
    }
}
