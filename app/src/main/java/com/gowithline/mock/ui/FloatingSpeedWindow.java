package com.gowithline.mock.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gowithline.mock.service.SimulationSnapshot;
import com.gowithline.mock.util.Prefs;

import java.util.Locale;

/**
 * Overlay window. Route mode keeps the compact speed panel; joystick mode uses
 * a single translucent joystick with speed shown on the knob and +/- buttons,
 * without a panel.
 */
public class FloatingSpeedWindow {
    public interface Listener {
        void onSpeedSelected(double kmh);

        void onJoystickMoved(double bearingDegrees, boolean active);

        void onClose();
    }

    private static final int BG_COLOR = 0xD91B2430;
    private static final int BUTTON_COLOR = 0x33FFFFFF;
    private static final int SELECTED_COLOR = 0xFF1E88E5;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SUBTLE_COLOR = 0xB3FFFFFF;
    private static final int GLASS_BUTTON_COLOR = 0x66000000;
    private static final double[] PRESETS = {5.0, 10.0, 20.0, 40.0};

    private final Context context;
    private final Listener listener;
    private final WindowManager windowManager;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final float density;
    private final WindowManager.LayoutParams params;

    private final FrameLayout root;
    private final LinearLayout panel;
    private final LinearLayout joystickOverlay;
    private final TextView speedText;
    private final TextView stateText;
    private final TextView[] presetViews = new TextView[PRESETS.length];
    private final View dragArea;
    private final JoystickView joystickView;

    private boolean showing;
    private double currentKmh = 5.0;
    private int currentState = SimulationSnapshot.STATE_IDLE;
    private int currentMode = SimulationSnapshot.MODE_ROUTE;
    private float downRawX;
    private float downRawY;
    private int startX;
    private int startY;
    private boolean dragging;

    public FloatingSpeedWindow(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.windowManager =
                (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        this.prefs = Prefs.get(this.context);
        this.density = this.context.getResources().getDisplayMetrics().density;

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                dp(200),
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt(Prefs.FLOATING_X, dp(16));
        params.y = prefs.getInt(Prefs.FLOATING_Y, dp(160));

        root = new FrameLayout(this.context);
        root.setBackgroundColor(0x00000000);

        panel = new LinearLayout(this.context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(rounded(BG_COLOR, dp(14)));
        panel.setElevation(dp(6));
        panel.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(panel);

        LinearLayout header = new LinearLayout(this.context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout dragContainer = new LinearLayout(this.context);
        dragContainer.setOrientation(LinearLayout.VERTICAL);
        dragContainer.setLayoutParams(
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        speedText = new TextView(this.context);
        speedText.setTextColor(TEXT_COLOR);
        speedText.setTextSize(22);
        speedText.setTypeface(Typeface.DEFAULT_BOLD);
        stateText = new TextView(this.context);
        stateText.setTextColor(SUBTLE_COLOR);
        stateText.setTextSize(11);
        stateText.setPadding(0, dp(2), 0, 0);
        dragContainer.addView(speedText);
        dragContainer.addView(stateText);
        dragArea = dragContainer;
        dragArea.setOnTouchListener(this::onDragTouch);

        header.addView(dragContainer);
        header.addView(makeCloseButton(32));
        panel.addView(header);

        LinearLayout adjustRow = new LinearLayout(this.context);
        adjustRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams adjustParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        adjustParams.topMargin = dp(6);
        adjustRow.setLayoutParams(adjustParams);
        TextView minus = makeButton("−", 1f);
        minus.setOnClickListener(v -> changeSpeed(-1.0));
        TextView plus = makeButton("+", 1f);
        plus.setOnClickListener(v -> changeSpeed(1.0));
        adjustRow.addView(minus);
        adjustRow.addView(plus);
        panel.addView(adjustRow);

        LinearLayout presetRow = new LinearLayout(this.context);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        presetParams.topMargin = dp(6);
        presetRow.setLayoutParams(presetParams);
        for (int i = 0; i < PRESETS.length; i++) {
            final double preset = PRESETS[i];
            TextView button = makeButton(String.format(Locale.US, "%.0f", preset), 1f);
            button.setOnClickListener(v -> selectSpeed(preset));
            presetViews[i] = button;
            presetRow.addView(button);
        }
        panel.addView(presetRow);

        joystickOverlay = new LinearLayout(this.context);
        joystickOverlay.setOrientation(LinearLayout.VERTICAL);
        joystickOverlay.setGravity(Gravity.CENTER_HORIZONTAL);
        joystickOverlay.setPadding(0, dp(2), 0, dp(4));
        joystickOverlay.setVisibility(View.GONE);
        joystickOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout joystickHeader = new LinearLayout(this.context);
        joystickHeader.setOrientation(LinearLayout.HORIZONTAL);
        joystickHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView grip = new TextView(this.context);
        grip.setText("—");
        grip.setTextColor(0x80FFFFFF);
        grip.setTextSize(14);
        grip.setGravity(Gravity.CENTER);
        grip.setLayoutParams(new LinearLayout.LayoutParams(
                0, dp(28), 1f));
        grip.setOnTouchListener(this::onDragTouch);
        joystickHeader.addView(grip);
        joystickHeader.addView(makeCloseButton(28));
        joystickOverlay.addView(joystickHeader);

        joystickView = new JoystickView(this.context);
        LinearLayout.LayoutParams joystickParams = new LinearLayout.LayoutParams(
                dp(144), dp(144));
        joystickParams.gravity = Gravity.CENTER_HORIZONTAL;
        joystickView.setLayoutParams(joystickParams);
        joystickView.setListener((bearingDegrees, active) -> {
            if (listener != null) {
                listener.onJoystickMoved(bearingDegrees, active);
            }
        });
        joystickView.setSpeedLabel("5.0");
        joystickOverlay.addView(joystickView);

        LinearLayout joystickSpeedRow = new LinearLayout(this.context);
        joystickSpeedRow.setOrientation(LinearLayout.HORIZONTAL);
        joystickSpeedRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams joystickSpeedParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        joystickSpeedParams.topMargin = dp(2);
        joystickSpeedRow.setLayoutParams(joystickSpeedParams);

        TextView joystickMinus = makeRoundButton("−");
        LinearLayout.LayoutParams minusParams =
                (LinearLayout.LayoutParams) joystickMinus.getLayoutParams();
        minusParams.setMargins(dp(14), 0, dp(14), 0);
        joystickMinus.setOnClickListener(v -> changeSpeed(-1.0));
        TextView joystickPlus = makeRoundButton("+");
        LinearLayout.LayoutParams plusParams =
                (LinearLayout.LayoutParams) joystickPlus.getLayoutParams();
        plusParams.setMargins(dp(14), 0, dp(14), 0);
        joystickPlus.setOnClickListener(v -> changeSpeed(1.0));
        joystickSpeedRow.addView(joystickMinus);
        joystickSpeedRow.addView(joystickPlus);
        joystickOverlay.addView(joystickSpeedRow);

        root.addView(joystickOverlay);

        updateTexts();
    }

    public void show(double kmh, int state, int mode) {
        mainHandler.post(() -> {
            currentKmh = clamp(kmh);
            currentState = state;
            currentMode = mode;
            updateTexts();
            if (showing) {
                return;
            }
            if (!Settings.canDrawOverlays(context) || windowManager == null) {
                return;
            }
            if (root.getParent() != null) {
                try {
                    windowManager.removeView(root);
                } catch (Exception ignored) {
                }
            }
            try {
                windowManager.addView(root, params);
                showing = true;
            } catch (Exception ignored) {
                showing = false;
            }
        });
    }

    public void update(double kmh, int state, int mode) {
        mainHandler.post(() -> {
            currentKmh = clamp(kmh);
            currentState = state;
            currentMode = mode;
            updateTexts();
        });
    }

    public void hide() {
        mainHandler.post(() -> {
            joystickView.setLocked(false);
            if (showing && windowManager != null) {
                try {
                    windowManager.removeView(root);
                } catch (Exception ignored) {
                }
            }
            showing = false;
        });
    }

    public boolean isShowing() {
        return showing;
    }

    private TextView makeCloseButton(int sizeDp) {
        TextView close = new TextView(context);
        close.setText("×");
        close.setTextColor(TEXT_COLOR);
        close.setTextSize(22);
        close.setGravity(Gravity.CENTER);
        close.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        close.setOnClickListener(v -> {
            hide();
            if (listener != null) {
                listener.onClose();
            }
        });
        return close;
    }

    private TextView makeButton(String label, float weight) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextColor(TEXT_COLOR);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(7), 0, dp(7));
        view.setBackground(rounded(BUTTON_COLOR, dp(8)));
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        layoutParams.setMargins(dp(2), 0, dp(2), 0);
        view.setLayoutParams(layoutParams);
        view.setClickable(true);
        return view;
    }

    private TextView makeRoundButton(String label) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextColor(TEXT_COLOR);
        view.setTextSize(20);
        view.setGravity(Gravity.CENTER);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        view.setBackground(rounded(GLASS_BUTTON_COLOR, dp(20)));
        view.setClickable(true);
        return view;
    }

    private void selectSpeed(double kmh) {
        currentKmh = clamp(kmh);
        updateTexts();
        if (listener != null) {
            listener.onSpeedSelected(currentKmh);
        }
    }

    private void changeSpeed(double deltaKmh) {
        selectSpeed(currentKmh + deltaKmh);
    }

    private void updateTexts() {
        boolean joystick = currentMode == SimulationSnapshot.MODE_JOYSTICK;
        boolean running = currentState == SimulationSnapshot.STATE_RUNNING;

        panel.setVisibility(joystick ? View.GONE : View.VISIBLE);
        joystickOverlay.setVisibility(joystick ? View.VISIBLE : View.GONE);

        speedText.setText(String.format(Locale.US, "%.1f km/h", currentKmh));
        joystickView.setSpeedLabel(String.format(Locale.US, "%.1f", currentKmh));

        if (joystick) {
            joystickView.setEnabled(running);
            if (!running) {
                joystickView.setLocked(false);
            }
        } else {
            joystickView.setEnabled(false);
            switch (currentState) {
                case SimulationSnapshot.STATE_RUNNING:
                    stateText.setText("模拟中 · 可快速切换");
                    break;
                case SimulationSnapshot.STATE_PAUSED:
                    stateText.setText("已暂停");
                    break;
                case SimulationSnapshot.STATE_FINISHED:
                    stateText.setText("已完成");
                    break;
                default:
                    stateText.setText("模拟速度");
                    break;
            }
            for (int i = 0; i < PRESETS.length; i++) {
                boolean selected = Math.abs(currentKmh - PRESETS[i]) < 0.05;
                presetViews[i].setBackground(
                        rounded(selected ? SELECTED_COLOR : BUTTON_COLOR, dp(8)));
            }
        }

        root.requestLayout();
        if (showing && windowManager != null) {
            try {
                windowManager.updateViewLayout(root, params);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean onDragTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = params.x;
                startY = params.y;
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE: {
                float deltaX = event.getRawX() - downRawX;
                float deltaY = event.getRawY() - downRawY;
                int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
                if (!dragging && Math.hypot(deltaX, deltaY) > touchSlop) {
                    dragging = true;
                }
                if (dragging && windowManager != null) {
                    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                    int width = root.getWidth() > 0 ? root.getWidth() : dp(200);
                    int height = root.getHeight() > 0 ? root.getHeight() : dp(220);
                    int maxX = Math.max(0, metrics.widthPixels - width);
                    int maxY = Math.max(0, metrics.heightPixels - height);
                    params.x = Math.max(0, Math.min(maxX, startX + (int) deltaX));
                    params.y = Math.max(0, Math.min(maxY, startY + (int) deltaY));
                    try {
                        windowManager.updateViewLayout(root, params);
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    prefs.edit()
                            .putInt(Prefs.FLOATING_X, params.x)
                            .putInt(Prefs.FLOATING_Y, params.y)
                            .apply();
                }
                dragging = false;
                return true;

            default:
                return false;
        }
    }

    private static double clamp(double kmh) {
        return Math.max(0.1, Math.min(300.0, kmh));
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    private static GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }
}
