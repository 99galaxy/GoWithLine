package com.gowithline.mock.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.gowithline.mock.R;
import com.gowithline.mock.geo.PointD;
import com.gowithline.mock.geo.RouteSimplifier;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.List;

/**
 * Transparent touch layer used while the user draws a route. The live stroke
 * is painted in screen space; when the finger lifts it is simplified and
 * handed to the activity, which adds it to the osmdroid polyline.
 */
public class DrawOverlayView extends View {
    public interface Listener {
        void onStrokeFinished(List<GeoPoint> points);
    }

    private static final double SIMPLIFY_EPSILON_METERS = 3.0;

    private final Paint underlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final List<PointF> liveScreen = new ArrayList<>();
    private final List<GeoPoint> liveGeo = new ArrayList<>();
    private final float minStepPx;
    private final float dotRadiusPx;

    private MapView mapView;
    private Listener listener;
    private boolean enabled;
    private boolean forwardingToMap;

    public DrawOverlayView(Context context) {
        this(context, null);
    }

    public DrawOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        minStepPx = 3.5f * density;
        dotRadiusPx = 5.0f * density;

        underlayPaint.setStyle(Paint.Style.STROKE);
        underlayPaint.setStrokeWidth(10.0f * density);
        underlayPaint.setStrokeCap(Paint.Cap.ROUND);
        underlayPaint.setStrokeJoin(Paint.Join.ROUND);
        underlayPaint.setColor(0xE6FFFFFF);

        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeWidth(6.0f * density);
        routePaint.setStrokeCap(Paint.Cap.ROUND);
        routePaint.setStrokeJoin(Paint.Join.ROUND);
        routePaint.setColor(ContextCompat.getColor(context, R.color.route_blue));

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(ContextCompat.getColor(context, R.color.route_blue));

        setWillNotDraw(false);
        setClickable(true);
        setFocusable(false);
    }

    public void setMapView(MapView mapView) {
        this.mapView = mapView;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setDrawingEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            forwardingToMap = false;
            cancelStroke();
        }
    }

    public boolean isDrawingEnabled() {
        return enabled;
    }

    public void cancelStroke() {
        liveScreen.clear();
        liveGeo.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (liveScreen.isEmpty()) {
            return;
        }

        if (liveScreen.size() >= 2) {
            path.reset();
            PointF first = liveScreen.get(0);
            path.moveTo(first.x, first.y);
            for (int i = 1; i < liveScreen.size(); i++) {
                PointF point = liveScreen.get(i);
                path.lineTo(point.x, point.y);
            }
            canvas.drawPath(path, underlayPaint);
            canvas.drawPath(path, routePaint);
        }

        PointF last = liveScreen.get(liveScreen.size() - 1);
        canvas.drawCircle(last.x, last.y, dotRadiusPx, dotPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!enabled || mapView == null) {
            return false;
        }

        int action = event.getActionMasked();

        if (forwardingToMap) {
            // A two-finger gesture has already been handed to the map. Keep
            // feeding it real events (pinch/zoom, two-finger pan) until the
            // last finger lifts.
            boolean gestureEnded = action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL;
            mapView.dispatchTouchEvent(event);
            if (gestureEnded) {
                forwardingToMap = false;
            }
            return true;
        }

        if (event.getPointerCount() > 1) {
            // The first finger was treated as the start of a stroke. Cancel
            // that stroke, synthesize the beginning of a map gesture, and
            // forward the rest to MapView so pinch/pan work while drawing.
            cancelStroke();
            beginMapGesture(event);
            forwardingToMap = true;
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                cancelStroke();
                addPoint(event.getX(), event.getY());
                return true;

            case MotionEvent.ACTION_MOVE: {
                if (liveScreen.isEmpty()) {
                    addPoint(event.getX(), event.getY());
                    return true;
                }
                PointF last = liveScreen.get(liveScreen.size() - 1);
                float dx = event.getX() - last.x;
                float dy = event.getY() - last.y;
                if (Math.hypot(dx, dy) >= minStepPx) {
                    addPoint(event.getX(), event.getY());
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
                performClick();
                finishStroke();
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelStroke();
                return true;

            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /**
     * MapView never saw the initial ACTION_DOWN because this view consumed it.
     * Build a synthetic DOWN + POINTER_DOWN from the current two fingers, then
     * let onTouchEvent forward the remaining real events to the map.
     */
    private void beginMapGesture(MotionEvent event) {
        int pointerCount = Math.min(2, event.getPointerCount());
        if (pointerCount < 2) {
            return;
        }

        // A running zoom/pan animation makes osmdroid reject the drag object.
        mapView.getController().stopAnimation(true);

        // Synthetic single-pointer DOWN for the first finger.
        MotionEvent.PointerProperties[] downProperties =
                new MotionEvent.PointerProperties[1];
        MotionEvent.PointerCoords[] downCoords = new MotionEvent.PointerCoords[1];
        downProperties[0] = new MotionEvent.PointerProperties();
        event.getPointerProperties(0, downProperties[0]);
        downCoords[0] = new MotionEvent.PointerCoords();
        event.getPointerCoords(0, downCoords[0]);

        MotionEvent syntheticDown = MotionEvent.obtain(
                event.getDownTime(), event.getEventTime(),
                MotionEvent.ACTION_DOWN, 1,
                downProperties, downCoords,
                event.getMetaState(), event.getButtonState(),
                event.getXPrecision(), event.getYPrecision(),
                event.getDeviceId(), 0, event.getSource(), event.getFlags());
        mapView.dispatchTouchEvent(syntheticDown);
        syntheticDown.recycle();

        // Synthetic POINTER_DOWN carrying both fingers.
        MotionEvent.PointerProperties[] properties =
                new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerCount];
        for (int i = 0; i < pointerCount; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            event.getPointerProperties(i, properties[i]);
            coords[i] = new MotionEvent.PointerCoords();
            event.getPointerCoords(i, coords[i]);
        }

        int actionIndex = event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                ? event.getActionIndex() : 1;
        actionIndex = Math.min(actionIndex, pointerCount - 1);
        int pointerDownAction = MotionEvent.ACTION_POINTER_DOWN
                | (actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        MotionEvent syntheticPointerDown = MotionEvent.obtain(
                event.getDownTime(), event.getEventTime(),
                pointerDownAction, pointerCount,
                properties, coords,
                event.getMetaState(), event.getButtonState(),
                event.getXPrecision(), event.getYPrecision(),
                event.getDeviceId(), 0, event.getSource(), event.getFlags());
        mapView.dispatchTouchEvent(syntheticPointerDown);
        syntheticPointerDown.recycle();
    }

    private void addPoint(float x, float y) {
        IGeoPoint geoPoint = mapView.getProjection().fromPixels((int) x, (int) y);
        if (geoPoint == null) {
            return;
        }
        liveScreen.add(new PointF(x, y));
        liveGeo.add(new GeoPoint(geoPoint.getLatitude(), geoPoint.getLongitude()));
        invalidate();
    }

    private void finishStroke() {
        if (liveGeo.size() >= 2 && listener != null) {
            List<PointD> raw = new ArrayList<>(liveGeo.size());
            for (GeoPoint point : liveGeo) {
                raw.add(new PointD(point.getLatitude(), point.getLongitude()));
            }
            List<PointD> simplified = RouteSimplifier.simplify(raw, SIMPLIFY_EPSILON_METERS);
            if (simplified.size() < 2) {
                simplified = raw;
            }
            List<GeoPoint> result = new ArrayList<>(simplified.size());
            for (PointD point : simplified) {
                result.add(new GeoPoint(point.lat, point.lng));
            }
            listener.onStrokeFinished(result);
        }
        cancelStroke();
    }
}
