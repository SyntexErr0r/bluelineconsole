package net.nhiroki.bluelineconsole.applock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PatternLockView extends View {
    public interface OnPatternListener {
        void onPatternCompleted(String patternDigits);
    }

    private static class Dot {
        final int id; // 1 to 9
        final int row; // 0 to 2
        final int col; // 0 to 2
        float x;
        float y;

        Dot(int id, int row, int col) {
            this.id = id;
            this.row = row;
            this.col = col;
        }
    }

    private final Dot[] mDots = new Dot[9];
    private final List<Dot> mSelectedDots = new ArrayList<>();
    private float mCurrentTouchX = -1;
    private float mCurrentTouchY = -1;
    private boolean mIsDrawing = false;
    private boolean mInputEnabled = true;

    private int mStateColor = Color.parseColor("#00f0ff"); // default cyber cyan
    private static final int COLOR_NORMAL = Color.parseColor("#00f0ff");
    private static final int COLOR_SUCCESS = Color.parseColor("#00ff99");
    private static final int COLOR_ERROR = Color.parseColor("#ff0055");

    private final Paint mDotNormalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mLinePath = new Path();

    private OnPatternListener mListener;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public PatternLockView(Context context) {
        super(context);
        init();
    }

    public PatternLockView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PatternLockView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int id = 1;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                mDots[id - 1] = new Dot(id, r, c);
                id++;
            }
        }

        mDotNormalPaint.setStyle(Paint.Style.FILL);
        mDotNormalPaint.setColor(Color.parseColor("#5000f0ff"));

        mDotRingPaint.setStyle(Paint.Style.STROKE);
        mDotRingPaint.setStrokeWidth(3f);
        mDotRingPaint.setColor(Color.parseColor("#3000f0ff"));

        mDotSelectedPaint.setStyle(Paint.Style.FILL);
        mDotSelectedPaint.setColor(mStateColor);

        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(10f);
        mLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mLinePaint.setStrokeJoin(Paint.Join.ROUND);
        mLinePaint.setColor(mStateColor);
    }

    public void setOnPatternListener(OnPatternListener listener) {
        this.mListener = listener;
    }

    public void setInputEnabled(boolean enabled) {
        this.mInputEnabled = enabled;
    }

    public void clearPattern() {
        mSelectedDots.clear();
        mCurrentTouchX = -1;
        mCurrentTouchY = -1;
        mIsDrawing = false;
        mStateColor = COLOR_NORMAL;
        updatePaints();
        invalidate();
    }

    public void showError() {
        mStateColor = COLOR_ERROR;
        updatePaints();
        invalidate();

        // Shake animation
        ValueAnimator shake = ValueAnimator.ofFloat(0, 16, -16, 12, -12, 6, -6, 0);
        shake.setDuration(450);
        shake.addUpdateListener(anim -> {
            float val = (float) anim.getAnimatedValue();
            setTranslationX(val);
        });
        shake.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mHandler.postDelayed(() -> {
                    clearPattern();
                    mInputEnabled = true;
                }, 400);
            }
        });
        shake.start();
    }

    public void showSuccess() {
        mStateColor = COLOR_SUCCESS;
        updatePaints();
        invalidate();
    }

    private void updatePaints() {
        mDotSelectedPaint.setColor(mStateColor);
        mLinePaint.setColor(mStateColor);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w > 0 ? w : 600, h > 0 ? h : 600);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float size = Math.min(w, h);
        float padding = size * 0.15f;
        float usableSize = size - 2 * padding;
        float step = usableSize / 2.0f;
        float startX = (w - usableSize) / 2.0f;
        float startY = (h - usableSize) / 2.0f;

        for (Dot d : mDots) {
            d.x = startX + d.col * step;
            d.y = startY + d.row * step;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float dotRadius = getWidth() * 0.035f;
        float ringRadius = getWidth() * 0.08f;

        // 1. Draw connecting lines between selected dots
        if (mSelectedDots.size() > 0) {
            mLinePath.reset();
            Dot first = mSelectedDots.get(0);
            mLinePath.moveTo(first.x, first.y);
            for (int i = 1; i < mSelectedDots.size(); i++) {
                Dot d = mSelectedDots.get(i);
                mLinePath.lineTo(d.x, d.y);
            }

            // Line to finger during active dragging
            if (mIsDrawing && mCurrentTouchX >= 0 && mCurrentTouchY >= 0) {
                mLinePath.lineTo(mCurrentTouchX, mCurrentTouchY);
            }
            canvas.drawPath(mLinePath, mLinePaint);
        }

        // 2. Draw all 9 dots
        for (Dot d : mDots) {
            boolean isSelected = mSelectedDots.contains(d);

            // Outer subtle circle
            canvas.drawCircle(d.x, d.y, ringRadius, mDotRingPaint);

            if (isSelected) {
                // Outer glow ring
                Paint glowRing = new Paint(Paint.ANTI_ALIAS_FLAG);
                glowRing.setStyle(Paint.Style.STROKE);
                glowRing.setStrokeWidth(6f);
                glowRing.setColor(mStateColor);
                canvas.drawCircle(d.x, d.y, ringRadius * 0.9f, glowRing);

                // Filled center dot
                canvas.drawCircle(d.x, d.y, dotRadius * 1.4f, mDotSelectedPaint);
            } else {
                // Normal unselected center dot
                canvas.drawCircle(d.x, d.y, dotRadius, mDotNormalPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInputEnabled) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                clearPattern();
                mIsDrawing = true;
                Dot hitDown = findClosestDot(x, y);
                if (hitDown != null) {
                    addDot(hitDown);
                }
                mCurrentTouchX = x;
                mCurrentTouchY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!mIsDrawing) return false;
                mCurrentTouchX = x;
                mCurrentTouchY = y;
                Dot hitMove = findClosestDot(x, y);
                if (hitMove != null && !mSelectedDots.contains(hitMove)) {
                    addIntermediateDotsIfNeeded(hitMove);
                    addDot(hitMove);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsDrawing = false;
                mCurrentTouchX = -1;
                mCurrentTouchY = -1;
                invalidate();

                if (mSelectedDots.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (Dot d : mSelectedDots) {
                        sb.append(d.id);
                    }
                    if (mListener != null) {
                        mListener.onPatternCompleted(sb.toString());
                    }
                }
                return true;
        }

        return super.onTouchEvent(event);
    }

    private void addDot(Dot dot) {
        if (!mSelectedDots.contains(dot)) {
            mSelectedDots.add(dot);
        }
    }

    private void addIntermediateDotsIfNeeded(Dot target) {
        if (mSelectedDots.isEmpty()) return;
        Dot last = mSelectedDots.get(mSelectedDots.size() - 1);

        int dRow = target.row - last.row;
        int dCol = target.col - last.col;

        // If spanning 2 rows or 2 cols in a straight line or diagonal, check center intermediate dot
        if (Math.abs(dRow) % 2 == 0 && Math.abs(dCol) % 2 == 0) {
            int midRow = last.row + dRow / 2;
            int midCol = last.col + dCol / 2;
            Dot mid = getDotAt(midRow, midCol);
            if (mid != null && !mSelectedDots.contains(mid)) {
                addDot(mid);
            }
        }
    }

    private Dot getDotAt(int row, int col) {
        if (row < 0 || row > 2 || col < 0 || col > 2) return null;
        for (Dot d : mDots) {
            if (d.row == row && d.col == col) return d;
        }
        return null;
    }

    private Dot findClosestDot(float x, float y) {
        float hitRadius = getWidth() * 0.12f;
        for (Dot d : mDots) {
            float dx = x - d.x;
            float dy = y - d.y;
            if (Math.hypot(dx, dy) <= hitRadius) {
                return d;
            }
        }
        return null;
    }
}
