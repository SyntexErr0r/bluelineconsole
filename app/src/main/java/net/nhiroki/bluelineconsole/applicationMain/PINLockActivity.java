package net.nhiroki.bluelineconsole.applicationMain;

import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState;

public class PINLockActivity extends BaseWindowActivity implements View.OnClickListener {
    private TextView pinLockIndicator;
    private final StringBuilder mCurrentInput = new StringBuilder();
    private String mStoredPin;

    public PINLockActivity() {
        super(R.layout.pin_lock_activity_body, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setHeaderFooterTexts("Blue Line Console", "Locked");

        mStoredPin = PreferenceManager.getDefaultSharedPreferences(this).getString("pref_app_lock_pin", "");
        if (mStoredPin.isEmpty()) {
            AppLockState.setLocked(false);
            setResult(RESULT_OK);
            finish();
            return;
        }

        pinLockIndicator = findViewById(R.id.pinLockIndicator);

        findViewById(R.id.btn0).setOnClickListener(this);
        findViewById(R.id.btn1).setOnClickListener(this);
        findViewById(R.id.btn2).setOnClickListener(this);
        findViewById(R.id.btn3).setOnClickListener(this);
        findViewById(R.id.btn4).setOnClickListener(this);
        findViewById(R.id.btn5).setOnClickListener(this);
        findViewById(R.id.btn6).setOnClickListener(this);
        findViewById(R.id.btn7).setOnClickListener(this);
        findViewById(R.id.btn8).setOnClickListener(this);
        findViewById(R.id.btn9).setOnClickListener(this);
        findViewById(R.id.btnDelete).setOnClickListener(this);
        findViewById(R.id.btnCancel).setOnClickListener(this);

        updateIndicator();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn0) mCurrentInput.append("0");
        else if (id == R.id.btn1) mCurrentInput.append("1");
        else if (id == R.id.btn2) mCurrentInput.append("2");
        else if (id == R.id.btn3) mCurrentInput.append("3");
        else if (id == R.id.btn4) mCurrentInput.append("4");
        else if (id == R.id.btn5) mCurrentInput.append("5");
        else if (id == R.id.btn6) mCurrentInput.append("6");
        else if (id == R.id.btn7) mCurrentInput.append("7");
        else if (id == R.id.btn8) mCurrentInput.append("8");
        else if (id == R.id.btn9) mCurrentInput.append("9");
        else if (id == R.id.btnDelete) {
            if (mCurrentInput.length() > 0) {
                mCurrentInput.deleteCharAt(mCurrentInput.length() - 1);
            }
        } else if (id == R.id.btnCancel) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        updateIndicator();
        checkPin();
    }

    private void updateIndicator() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mCurrentInput.length(); i++) {
            sb.append("●");
        }
        pinLockIndicator.setText(sb.toString());
    }

    private void checkPin() {
        if (mCurrentInput.length() >= mStoredPin.length()) {
            if (mCurrentInput.toString().equals(mStoredPin)) {
                AppLockState.setLocked(false);
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                mCurrentInput.setLength(0);
                updateIndicator();
            }
        }
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}
