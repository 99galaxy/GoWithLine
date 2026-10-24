package com.gowithline.mock.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * Minimal self-drawn joystick. It reports a bearing and an active flag; the
 * configured simulation speed is applied by the service, so the deflection
 * distance only gates movement (dead zone), it does not scale speed.
 */
public class JoystickView extends View {
    public interface Listener {
        void onJoystickMoved(float bearingDegrees, boolean active);
    }

    private static final float DEAD_ZONE_RATIO = 0.15f;

    private final Paint baseFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint northPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path northPath = new Path();
    private final float density;

    private Listener listener;
    private float centerX;
    private float centerY;
    private float baseRadius;
    private float knobRadius;
    private float knobX;
    private float knobY;
    private float lastBearing;
    private float lastUnitX;
    private float lastUnitY;
    private float lastRatio;
    private boolean hasDirection;
    private boolean locked;
    private boolean active;
    private float lastNotifiedBearing = Float.NaN;
    private boolean lastNotifiedActive;
    private String speedLabel = "10.0";
    private float downX;
    private float downY;
    private float knobStartX;
    private float knobStartY;
    private boolean tapCandidate;
    private boolean movedFromDown;
    private ValueAnimator knobAnimator;

    public JoystickView(Context context) {
        this(context, null);
    }

    public JoystickView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        baseFillPaint.setStyle(Paint.Style.FILL);
        baseFillPaint.setColor(0x30000000);
        baseBorderPaint.setStyle(Paint.Style.STROKE);
        baseBorderPaint.setStrokeWidth(dp(2));
        baseBorderPaint.setColor(0x66FFFFFF);
        knobPaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(0xCC1E88E5);
        knobBorderPaint.setStyle(Paint.Style.STROKE);
        knobBorderPaint.setStrokeWidth(dp(2));
        knobBorderPaint.setColor(0xCCFFFFFF);
        northPaint.setStyle(Paint.Style.FILL);
        northPaint.setColor(0x80FFFFFF);

        speedPaint.setColor(0xFFFFFFFF);
        speedPaint.setTextSize(dp(12));
        speedPaint.setTextAlign(Paint.Align.CENTER);
        speedPaint.setTypeface(Typeface.DEFAULT_BOLD);
        unitPaint.setColor(0xB3FFFFFF);
        unitPaint.setTextSize(dp(8));
        unitPaint.setTextAlign(Paint.Align.CENTER);
        lockPaint.setColor(0xCCFFFFFF);
        lockPaint.setTextSize(dp(9));
        lockPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSpeedLabel(String label) {
        speedLabel = label == null ? "" : label;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        baseRadius = Math.min(w, h) / 2f - dp(4);
        knobRadius = baseRadius * 0.38f;
        knobX = centerX;
        knobY = centerY;

        float northSize = dp(7);
        northPath.reset();
        northPath.moveTo(centerX, centerY - baseRadius + dp(10));
        northPath.lineTo(centerX - northSize / 2f, centerY - baseRadius + dp(10) + northSize);
        northPath.lineTo(centerX + northSize / 2f, centerY - baseRadius + dp(10) + northSize);
        northPath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(centerX, centerY, baseRadius, baseFillPaint);
        canvas.drawCircle(centerX, centerY, baseRadius, baseBorderPaint);
        canvas.drawPath(northPath, northPaint);
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint);
        canvas.drawCircle(knobX, knobY, knobRadius, knobBorderPaint);

        lockPaint.setAlpha(locked ? 0xFF : 0x99);
        canvas.drawText("锁", knobX, knobY - knobRadius * 0.38f, lockPaint);
        canvas.drawText(speedLabel, knobX, knobY + dp(1), speedPaint);
        canvas.drawText("km/h", knobX, knobY + dp(13), unitPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isInsideBase(event.getX(), event.getY())) {
                    return false;
                }
                cancelKnobAnimation();
                downX = event.getX();
                downY = event.getY();
                knobStartX = knobX;
                knobStartY = knobY;
                movedFromDown = false;
                tapCandidate = Math.hypot(
                        event.getX() - knobX, event.getY() - knobY)
                        <= knobRadius + dp(12);
                if (!tapCandidate && !locked) {
                    moveKnob(event.getX(), event.getY());
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (tapCandidate && !movedFromDown) {
                    if (Math.hypot(event.getX() - downX, event.getY() - downY)
                            <= touchSlopForTap()) {
                        return true;
                    }
                    movedFromDown = true;
                }
                if (locked) {
                    // Locked: drag relative to the current knob position so the
                    // knob glides with the finger instead of jumping to the
                    // touch point.
                    moveKnob(knobStartX + (event.getX() - downX),
                            knobStartY + (event.getY() - downY));
                } else {
                    moveKnob(event.getX(), event.getY());
                }
                return true;

            case MotionEvent.ACTION_UP:
                performClick();
                if (tapCandidate && !movedFromDown) {
                    // Tap the knob to toggle lock/unlock.
                    setLocked(!locked);
                } else if (locked) {
                    applyLockedPosition(true);
                } else {
                    resetKnob(true);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (locked) {
                    applyLockedPosition(true);
                } else {
                    resetKnob(true);
                }
                return true;

            default:
                return true;
        }
    }

    private float touchSlopForTap() {
        return dp(8);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            locked = false;
            resetKnob(true);
        }
    }

    /**
     * Locks the last direction: releasing the finger keeps the knob deflected
     * and keeps the owner moving. Unlocking recenters the knob and stops.
     */
    public void setLocked(boolean locked) {
        if (this.locked == locked) {
            if (locked && hasDirection) {
                applyLockedPosition(true);
            }
            return;
        }
        this.locked = locked;
        if (locked) {
            if (hasDirection) {
                applyLockedPosition(true);
            } else {
                active = false;
            }
        } else {
            resetKnobAnimated();
        }
        invalidate();
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean hasDirection() {
        return hasDirection;
    }

    public void reset() {
        locked = false;
        resetKnob(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        locked = false;
        resetKnob(true);
        super.onDetachedFromWindow();
    }

    private boolean isInsideBase(float x, float y) {
        return Math.hypot(x - centerX, y - centerY) <= baseRadius + dp(12);
    }

    private void moveKnob(float x, float y) {
        float dx = x - centerX;
        float dy = y - centerY;
        float distance = (float) Math.hypot(dx, dy);
        float maxDistance = Math.max(1f, baseRadius - knobRadius);
        float clamped = Math.min(distance, maxDistance);
        float ratio = clamped / maxDistance;

        if (distance > 0.001f) {
            knobX = centerX + dx / distance * clamped;
            knobY = centerY + dy / distance * clamped;
        } else {
            knobX = centerX;
            knobY = centerY;
        }

        if (distance > 0.001f) {
            lastUnitX = dx / distance;
            lastUnitY = dy / distance;
            hasDirection = true;
            // Screen angle -> clockwise bearing from north.
            lastBearing = (float) ((Math.toDegrees(Math.atan2(dx, -dy)) + 360.0) % 360.0);
        }
        lastRatio = ratio;
        active = ratio > DEAD_ZONE_RATIO;
        notifyListener();
        invalidate();
    }

    /**
     * Coalesces notifications: a drag produces 60-120 touch events per second
     * but the service only needs the direction when it meaningfully changes,
     * so report on &gt;1° bearing deltas or active-flag flips only.
     */
    private void notifyListener() {
        if (listener == null) {
            return;
        }
        if (!Float.isNaN(lastNotifiedBearing) && lastNotifiedActive == active) {
            float delta = Math.abs(lastBearing - lastNotifiedBearing);
            if (delta > 180.0f) {
                delta = 360.0f - delta;
            }
            if (delta <= 1.0f) {
                return;
            }
        }
        lastNotifiedBearing = lastBearing;
        lastNotifiedActive = active;
        listener.onJoystickMoved(lastBearing, active);
    }

    private void applyLockedPosition(boolean animate) {
        if (!hasDirection) {
            active = false;
            invalidate();
            return;
        }
        float maxDistance = Math.max(1f, baseRadius - knobRadius);
        float ratio = Math.max(0.7f, lastRatio);
        float targetX = centerX + lastUnitX * maxDistance * ratio;
        float targetY = centerY + lastUnitY * maxDistance * ratio;
        if (animate) {
            animateKnobTo(targetX, targetY);
        } else {
            knobX = targetX;
            knobY = targetY;
        }
        active = true;
        notifyListener();
        invalidate();
    }

    private void resetKnob(boolean notify) {
        cancelKnobAnimation();
        knobX = centerX;
        knobY = centerY;
        boolean wasActive = active;
        active = false;
        invalidate();
        if (notify) {
            notifyListener();
        }
    }

    private void resetKnobAnimated() {
        active = false;
        animateKnobTo(centerX, centerY);
        notifyListener();
    }

    private void animateKnobTo(float targetX, float targetY) {
        cancelKnobAnimation();
        final float startX = knobX;
        final float startY = knobY;
        if (Math.abs(startX - targetX) < 0.5f && Math.abs(startY - targetY) < 0.5f) {
            knobX = targetX;
            knobY = targetY;
            invalidate();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(160);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            knobX = startX + (targetX - startX) * t;
            knobY = startY + (targetY - startY) * t;
            invalidate();
        });
        knobAnimator = animator;
        animator.start();
    }

    private void cancelKnobAnimation() {
        if (knobAnimator != null) {
            knobAnimator.cancel();
            knobAnimator = null;
        }
    }

    private float dp(float value) {
        return value * density;
    }
}
