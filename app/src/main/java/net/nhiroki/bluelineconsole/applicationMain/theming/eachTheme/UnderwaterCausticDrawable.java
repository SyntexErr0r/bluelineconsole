package net.nhiroki.bluelineconsole.applicationMain.theming.eachTheme;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class UnderwaterCausticDrawable extends Drawable implements Animatable {
    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCausticPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCausticPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCornerMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mBoundsRect = new RectF();
    private final Matrix mMatrix1 = new Matrix();
    private final Matrix mMatrix2 = new Matrix();

    private ValueAnimator mAnimator;
    private float mProgress = 0f;
    private float mCornerRadius = 24f; // 8dp equivalent, updated in setDensity
    private float mDensity = 3.0f;

    @ColorInt private int mAccentColor = Color.parseColor("#00f0ff");
    private boolean mAlertMode = false;

    public UnderwaterCausticDrawable() {
        this(Color.parseColor("#00f0ff"));
    }

    public UnderwaterCausticDrawable(@ColorInt int accentColor) {
        this.mAccentColor = accentColor;

        mBgPaint.setStyle(Paint.Style.FILL);
        mBgPaint.setColor(Color.parseColor("#55020912")); // Frosted deep cyber glass

        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(3.5f);
        mBorderPaint.setColor(accentColor);

        mCornerMarkPaint.setStyle(Paint.Style.STROKE);
        mCornerMarkPaint.setStrokeWidth(5f);
        mCornerMarkPaint.setColor(accentColor);

        mCausticPaint1.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        mCausticPaint2.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));

        initAnimator();
    }

    public void setDensity(float density) {
        this.mDensity = density;
        this.mCornerRadius = 8f * density;
        this.mBorderPaint.setStrokeWidth(1.5f * density);
        this.mCornerMarkPaint.setStrokeWidth(2.5f * density);
        invalidateSelf();
    }

    public void setAccentColor(@ColorInt int color) {
        this.mAccentColor = color;
        this.mBorderPaint.setColor(color);
        this.mCornerMarkPaint.setColor(color);
        rebuildShaders();
        invalidateSelf();
    }

    public void setAlertMode(boolean alert) {
        this.mAlertMode = alert;
        if (alert) {
            int redAlert = Color.parseColor("#ff0055");
            mBorderPaint.setColor(redAlert);
            mCornerMarkPaint.setColor(redAlert);
        } else {
            mBorderPaint.setColor(mAccentColor);
            mCornerMarkPaint.setColor(mAccentColor);
        }
        rebuildShaders();
        invalidateSelf();
    }

    private void initAnimator() {
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(7000L); // 7s slow, natural undulating cycle
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setRepeatMode(ValueAnimator.RESTART);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(animation -> {
            mProgress = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        mBoundsRect.set(bounds);
        rebuildShaders();
    }

    private void rebuildShaders() {
        float width = mBoundsRect.width();
        float height = mBoundsRect.height();
        if (width <= 0 || height <= 0) {
            return;
        }

        int primaryColor = mAlertMode ? Color.parseColor("#ff0055") : mAccentColor;
        int secondaryColor = mAlertMode ? Color.parseColor("#ff4400") : Color.parseColor("#00e5a3");

        int c1 = Color.argb(0, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor));
        int c2 = Color.argb(55, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor));
        int c3 = Color.argb(0, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor));
        int c4 = Color.argb(45, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor));
        int c5 = Color.argb(0, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor));

        // Primary caustic band shader (angled waves)
        LinearGradient shader1 = new LinearGradient(
                0, 0, width * 0.7f, height * 0.9f,
                new int[]{c1, c2, c3, c4, c5, c1},
                new float[]{0.0f, 0.22f, 0.45f, 0.70f, 0.88f, 1.0f},
                Shader.TileMode.REPEAT
        );
        mCausticPaint1.setShader(shader1);

        // Secondary cross-harmonic wave shader (simulating water surface refraction interference)
        int ca = Color.argb(0, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor));
        int cb = Color.argb(40, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor));
        int cc = Color.argb(0, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor));

        LinearGradient shader2 = new LinearGradient(
                width * 0.8f, 0, 0, height,
                new int[]{ca, cb, cc, cb, ca},
                new float[]{0.0f, 0.3f, 0.55f, 0.8f, 1.0f},
                Shader.TileMode.REPEAT
        );
        mCausticPaint2.setShader(shader2);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mBoundsRect.isEmpty()) {
            return;
        }

        // 1. Draw dark frosted glass base
        canvas.drawRoundRect(mBoundsRect, mCornerRadius, mCornerRadius, mBgPaint);

        // 2. Animated Caustics (Underwater solar light waves)
        float w = mBoundsRect.width();
        float h = mBoundsRect.height();
        if (w > 0 && h > 0) {
            canvas.save();
            // Clip to rounded window boundaries
            android.graphics.Path clipPath = new android.graphics.Path();
            clipPath.addRoundRect(mBoundsRect, mCornerRadius, mCornerRadius, android.graphics.Path.Direction.CW);
            canvas.clipPath(clipPath);

            // First harmonic: undulating wave translation + subtle oscillating angle
            float phase1 = mProgress * 2f * (float) Math.PI;
            float dx1 = (float) Math.sin(phase1) * (w * 0.18f) + (mProgress * w);
            float dy1 = (float) Math.cos(phase1) * (h * 0.12f) + (mProgress * h * 0.5f);

            mMatrix1.reset();
            mMatrix1.setTranslate(dx1 % (w * 0.7f), dy1 % (h * 0.9f));
            if (mCausticPaint1.getShader() != null) {
                mCausticPaint1.getShader().setLocalMatrix(mMatrix1);
                canvas.drawRect(mBoundsRect, mCausticPaint1);
            }

            // Second cross-harmonic: counter-oscillation creating water caustic interference
            float phase2 = (mProgress * 1.35f) * 2f * (float) Math.PI;
            float dx2 = (float) Math.cos(phase2) * (w * 0.22f) - (mProgress * w * 0.8f);
            float dy2 = (float) Math.sin(phase2) * (h * 0.15f) + (mProgress * h * 0.3f);

            mMatrix2.reset();
            mMatrix2.setTranslate(dx2 % (w * 0.8f), dy2 % h);
            if (mCausticPaint2.getShader() != null) {
                mCausticPaint2.getShader().setLocalMatrix(mMatrix2);
                canvas.drawRect(mBoundsRect, mCausticPaint2);
            }

            canvas.restore();
        }

        // 3. Glowing Cyber Border
        canvas.drawRoundRect(mBoundsRect, mCornerRadius, mCornerRadius, mBorderPaint);

        // 4. Futuristic HUD Corner Brackets
        drawCornerBrackets(canvas);
    }

    private void drawCornerBrackets(Canvas canvas) {
        float bracketLen = 14f * mDensity;
        float inset = mBorderPaint.getStrokeWidth() / 2f;

        float l = mBoundsRect.left + inset;
        float t = mBoundsRect.top + inset;
        float r = mBoundsRect.right - inset;
        float b = mBoundsRect.bottom - inset;

        // Top-Left
        canvas.drawLine(l, t + mCornerRadius, l, t + mCornerRadius + bracketLen, mCornerMarkPaint);
        canvas.drawLine(l + mCornerRadius, t, l + mCornerRadius + bracketLen, t, mCornerMarkPaint);

        // Top-Right
        canvas.drawLine(r, t + mCornerRadius, r, t + mCornerRadius + bracketLen, mCornerMarkPaint);
        canvas.drawLine(r - mCornerRadius, t, r - mCornerRadius - bracketLen, t, mCornerMarkPaint);

        // Bottom-Left
        canvas.drawLine(l, b - mCornerRadius, l, b - mCornerRadius - bracketLen, mCornerMarkPaint);
        canvas.drawLine(l + mCornerRadius, b, l + mCornerRadius + bracketLen, b, mCornerMarkPaint);

        // Bottom-Right
        canvas.drawLine(r, b - mCornerRadius, r, b - mCornerRadius - bracketLen, mCornerMarkPaint);
        canvas.drawLine(r - mCornerRadius, b, r - mCornerRadius - bracketLen, b, mCornerMarkPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mBgPaint.setAlpha(alpha);
        mBorderPaint.setAlpha(alpha);
        mCausticPaint1.setAlpha(alpha);
        mCausticPaint2.setAlpha(alpha);
        mCornerMarkPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mBgPaint.setColorFilter(colorFilter);
        mBorderPaint.setColorFilter(colorFilter);
        mCausticPaint1.setColorFilter(colorFilter);
        mCausticPaint2.setColorFilter(colorFilter);
        mCornerMarkPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void start() {
        if (mAnimator != null && !mAnimator.isRunning()) {
            mAnimator.start();
        }
    }

    @Override
    public void stop() {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
    }

    @Override
    public boolean isRunning() {
        return mAnimator != null && mAnimator.isRunning();
    }
}
