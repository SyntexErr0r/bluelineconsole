package net.nhiroki.bluelineconsole.applock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Sci-Fi 11-Node Cyber Matrix PatternLockView (3 - 5 - 3 Layout)
 *
 * Layout:
 *        [1]       [2]       [3]
 *  [4]   [5]     (CORE)      [6]   [7]
 *        [8]       [9]       [0]
 *
 * Supports:
 * - Direct 0-9 decimal digit swiping (contains dot '0').
 * - Central Core (◎ / 'C') that acts as an anchor / repeat bridge for duplicate digits (e.g. 2 -> Core -> 2).
 * - High-tech neon glow, concentric rings, and monospace HUD labels.
 */
public class PatternLockView extends View {
    public interface OnPatternListener {
        void onPatternCompleted(String patternDigits);
    }

    public static class Dot {
        public final char id; // '1'..'9', '0', 'C'
        public final String label;
        public final int row; // 0..2
        public final int col; // 0..4
        public final boolean isCore;
        public float x;
        public float y;

        public Dot(char id, String label, int row, int col, boolean isCore) {
            this.id = id;
            this.label = label;
            this.row = row;
            this.col = col;
            this.isCore = isCore;
        }
    }

    private final Dot[] mDots = new Dot[11];
    private final List<Dot> mSelectedDots = new ArrayList<>();
    private float mCurrentTouchX = -1;
    private float mCurrentTouchY = -1;
    private boolean mIsDrawing = false;
    private boolean mInputEnabled = true;

    private int mNormalColor = Color.parseColor("#00f0ff");
    private int mStateColor = Color.parseColor("#00f0ff");
    private static final int COLOR_SUCCESS = Color.parseColor("#00ff99");
    private static final int COLOR_ERROR = Color.parseColor("#ff0055");

    private final Paint mDotNormalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCoreRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        // Row 0: 3 dots ('1', '2', '3') at columns 1, 2, 3
        mDots[0] = new Dot('1', "1", 0, 1, false);
        mDots[1] = new Dot('2', "2", 0, 2, false);
        mDots[2] = new Dot('3', "3", 0, 3, false);

        // Row 1: 5 dots ('4', '5', 'C' (Core), '6', '7') at columns 0, 1, 2, 3, 4
        mDots[3] = new Dot('4', "4", 1, 0, false);
        mDots[4] = new Dot('5', "5", 1, 1, false);
        mDots[5] = new Dot('C', "◎", 1, 2, true); // Center Cyber Core
        mDots[6] = new Dot('6', "6", 1, 3, false);
        mDots[7] = new Dot('7', "7", 1, 4, false);

        // Row 2: 3 dots ('8', '9', '0') at columns 1, 2, 3
        mDots[8] = new Dot('8', "8", 2, 1, false);
        mDots[9] = new Dot('9', "9", 2, 2, false);
        mDots[10] = new Dot('0', "0", 2, 3, false);

        mDotNormalPaint.setStyle(Paint.Style.FILL);
        mDotRingPaint.setStyle(Paint.Style.STROKE);
        mDotRingPaint.setStrokeWidth(3f);
        mDotSelectedPaint.setStyle(Paint.Style.FILL);

        mCoreRingPaint.setStyle(Paint.Style.STROKE);
        mCoreRingPaint.setStrokeWidth(2.5f);

        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(9f);
        mLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mLinePaint.setStrokeJoin(Paint.Join.ROUND);

        updatePaints();
    }

    public void setAccentColor(int color) {
        this.mNormalColor = color;
        this.mStateColor = color;
        updatePaints();
        invalidate();
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
        mStateColor = mNormalColor;
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
        mDotNormalPaint.setColor((mNormalColor & 0x00ffffff) | 0x50000000);
        mDotRingPaint.setColor((mNormalColor & 0x00ffffff) | 0x40000000);
        mDotSelectedPaint.setColor(mStateColor);
        mCoreRingPaint.setColor((mStateColor & 0x00ffffff) | 0x90000000);
        mLinePaint.setColor(mStateColor);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int size;
        if (widthMode != MeasureSpec.UNSPECIFIED && heightMode != MeasureSpec.UNSPECIFIED) {
            size = Math.min(widthSize, heightSize);
        } else if (widthMode != MeasureSpec.UNSPECIFIED) {
            size = widthSize;
        } else if (heightMode != MeasureSpec.UNSPECIFIED) {
            size = heightSize;
        } else {
            size = (int) (260 * getResources().getDisplayMetrics().density);
        }
        if (size <= 0) {
            size = (int) (260 * getResources().getDisplayMetrics().density);
        }
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float paddingX = w * 0.08f;
        float paddingY = h * 0.12f;
        float usableW = w - 2 * paddingX;
        float usableH = h - 2 * paddingY;

        float colStep = usableW / 4.0f; // 5 columns across row 1: cols 0..4
        float rowStep = usableH / 2.0f; // 3 rows: rows 0..2
        float startX = (w - usableW) / 2.0f;
        float startY = (h - usableH) / 2.0f;

        for (Dot d : mDots) {
            d.x = startX + d.col * colStep;
            d.y = startY + d.row * rowStep;
        }

        mTextPaint.setTextSize(getWidth() * 0.045f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float dotRadius = getWidth() * 0.032f;
        float ringRadius = getWidth() * 0.075f;

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

        // 2. Draw all 11 cyber nodes
        for (Dot d : mDots) {
            boolean isSelected = mSelectedDots.contains(d);

            if (d.isCore) {
                // Central Cyber Core: concentric glowing rings
                canvas.drawCircle(d.x, d.y, ringRadius * 1.15f, mCoreRingPaint);
                canvas.drawCircle(d.x, d.y, ringRadius * 0.65f, mCoreRingPaint);

                if (isSelected) {
                    Paint glowRing = new Paint(Paint.ANTI_ALIAS_FLAG);
                    glowRing.setStyle(Paint.Style.STROKE);
                    glowRing.setStrokeWidth(5f);
                    glowRing.setColor(mStateColor);
                    canvas.drawCircle(d.x, d.y, ringRadius * 1.15f, glowRing);
                    canvas.drawCircle(d.x, d.y, dotRadius * 1.5f, mDotSelectedPaint);
                } else {
                    canvas.drawCircle(d.x, d.y, dotRadius * 1.2f, mDotNormalPaint);
                }
            } else {
                // Outer subtle ring
                canvas.drawCircle(d.x, d.y, ringRadius, mDotRingPaint);

                if (isSelected) {
                    // Outer glow ring
                    Paint glowRing = new Paint(Paint.ANTI_ALIAS_FLAG);
                    glowRing.setStyle(Paint.Style.STROKE);
                    glowRing.setStrokeWidth(5f);
                    glowRing.setColor(mStateColor);
                    canvas.drawCircle(d.x, d.y, ringRadius * 0.9f, glowRing);

                    // Filled center dot
                    canvas.drawCircle(d.x, d.y, dotRadius * 1.4f, mDotSelectedPaint);
                } else {
                    // Normal unselected center dot
                    canvas.drawCircle(d.x, d.y, dotRadius, mDotNormalPaint);
                }
            }

            // Draw Node Text Label slightly offset below/center
            float textOffset = isSelected ? (ringRadius * 0.45f) : (ringRadius * 0.45f);
            mTextPaint.setColor(isSelected ? mStateColor : ((mNormalColor & 0x00ffffff) | 0x88000000));
            mTextPaint.setTextSize(getWidth() * (d.isCore ? 0.040f : 0.038f));
            canvas.drawText(d.label, d.x, d.y + textOffset, mTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInputEnabled) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
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
                if (hitMove != null) {
                    Dot last = mSelectedDots.isEmpty() ? null : mSelectedDots.get(mSelectedDots.size() - 1);
                    if (hitMove != last) {
                        // Allow node if unvisited, OR if it's the Core, OR if coming directly out of Core (repeat bridge!)
                        if (!mSelectedDots.contains(hitMove) || hitMove.isCore || (last != null && last.isCore)) {
                            addIntermediateDotsIfNeeded(hitMove);
                            addDot(hitMove);
                        }
                    }
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
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
        Dot last = mSelectedDots.isEmpty() ? null : mSelectedDots.get(mSelectedDots.size() - 1);
        if (dot != last) {
            mSelectedDots.add(dot);
        }
    }

    private void addIntermediateDotsIfNeeded(Dot target) {
        if (mSelectedDots.isEmpty()) return;
        Dot last = mSelectedDots.get(mSelectedDots.size() - 1);

        int dRow = target.row - last.row;
        int dCol = target.col - last.col;

        // Straight or diagonal jumps across an intermediate dot
        if (Math.abs(dRow) % 2 == 0 && Math.abs(dCol) % 2 == 0 && (Math.abs(dRow) == 2 || Math.abs(dCol) == 2)) {
            int midRow = last.row + dRow / 2;
            int midCol = last.col + dCol / 2;
            Dot mid = getDotAt(midRow, midCol);
            if (mid != null && !mSelectedDots.contains(mid)) {
                addDot(mid);
            }
        }
    }

    private Dot getDotAt(int row, int col) {
        for (Dot d : mDots) {
            if (d.row == row && d.col == col) return d;
        }
        return null;
    }

    private Dot findClosestDot(float x, float y) {
        float hitRadius = getWidth() * 0.10f;
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
