package com.gowithline.mock.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.location.LocationCompat;

import com.gowithline.mock.MainActivity;
import com.gowithline.mock.R;
import com.gowithline.mock.geo.GeoMath;
import com.gowithline.mock.geo.PointD;
import com.gowithline.mock.geo.RoutePath;
import com.gowithline.mock.geo.RouteSimulator;
import com.gowithline.mock.ui.FloatingSpeedWindow;
import com.gowithline.mock.util.FormatUtils;
import com.gowithline.mock.util.MockLocationHelper;
import com.gowithline.mock.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Foreground service that drives both GPS and NETWORK test providers, using
 * the same mechanism as ZCShou/GoGoGo: a developer-option mock location app
 * calls addTestProvider + setTestProviderLocation without root.
 */
public class MockLocationService extends Service {
    private static final String TAG = "GoWithLineService";

    public static final String ACTION_START = "com.gowithline.mock.action.START";
    public static final String ACTION_PAUSE = "com.gowithline.mock.action.PAUSE";
    public static final String ACTION_RESUME = "com.gowithline.mock.action.RESUME";
    public static final String ACTION_STOP = "com.gowithline.mock.action.STOP";
    public static final String EXTRA_LATS = "com.gowithline.mock.extra.LATS";
    public static final String EXTRA_LNGS = "com.gowithline.mock.extra.LNGS";
    public static final String EXTRA_SPEED_MPS = "com.gowithline.mock.extra.SPEED_MPS";
    public static final String EXTRA_LOOP = "com.gowithline.mock.extra.LOOP";
    public static final String EXTRA_MODE = "com.gowithline.mock.extra.MODE";
    public static final String EXTRA_START_LAT = "com.gowithline.mock.extra.START_LAT";
    public static final String EXTRA_START_LNG = "com.gowithline.mock.extra.START_LNG";

    private static final String CHANNEL_ID = "gowithline_mock_location";
    private static final String FUSED_PROVIDER_NAME = "fused";
    private static final int NOTIFICATION_ID = 1001;
    private static final double ALTITUDE_METERS = 50.0;
    private static final long RUNNING_INTERVAL_MS = 200L;
    private static final long HOLD_INTERVAL_MS = 1000L;
    private static final long NOTIFICATION_INTERVAL_MS = 1000L;
    private static final double MIN_SPEED_MPS = 0.01;
    private static final double MAX_SPEED_MPS = 100.0;

    public interface Listener {
        void onSnapshot(SimulationSnapshot snapshot);
    }

    public class LocalBinder extends Binder {
        public MockLocationService getService() {
            return MockLocationService.this;
        }
    }

    private final LocalBinder binder = new LocalBinder();
    private Runnable errorStopRunnable;

    private LocationManager locationManager;
    private NotificationManager notificationManager;
    private HandlerThread workerThread;
    private Handler workerHandler;
    private Handler mainHandler;
    private PowerManager.WakeLock wakeLock;

    private RouteSimulator simulator;
    private volatile int state = SimulationSnapshot.STATE_IDLE;
    private int mode = SimulationSnapshot.MODE_ROUTE;
    private double speedMps = 1.4;
    private boolean loop;
    private double joyLat = Double.NaN;
    private double joyLng = Double.NaN;
    private float joyBearing;
    private boolean joyActive;
    private double joyTravelledMeters;
    private boolean gpsReady;
    private boolean networkReady;
    private boolean fusedReady;
    private String gpsError;
    private String networkError;
    private String fusedError;
    private String lastProviderError;
    private int injectedCount;
    private String lastInjectionError;
    private int consecutiveInjectionFailures;
    private int echoCount;
    private double echoLat = Double.NaN;
    private double echoLng = Double.NaN;
    private double injectedLat = Double.NaN;
    private double injectedLng = Double.NaN;
    private long echoElapsedMs;
    private LocationListener echoListener;
    private FloatingSpeedWindow floatingWindow;
    private volatile boolean foregroundStarted;
    private volatile boolean destroyed;
    private long lastTickElapsed;
    private long lastNotificationElapsed;
    private long revision;

    private volatile SimulationSnapshot snapshot = SimulationSnapshot.idle();
    private volatile ServiceDiagnostics diagnostics = ServiceDiagnostics.idle();
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || state == SimulationSnapshot.STATE_IDLE
                    || state == SimulationSnapshot.STATE_ERROR) {
                return;
            }

            long now = SystemClock.elapsedRealtime();
            if (state == SimulationSnapshot.STATE_RUNNING) {
                long delta = now - lastTickElapsed;
                lastTickElapsed = now;
                if (mode == SimulationSnapshot.MODE_JOYSTICK) {
                    if (joyActive && speedMps > 0.0 && delta > 0) {
                        double distance = speedMps * (delta / 1000.0);
                        PointD next = GeoMath.destinationPoint(
                                new PointD(joyLat, joyLng), joyBearing, distance);
                        joyLat = next.lat;
                        joyLng = next.lng;
                        joyTravelledMeters += distance;
                    }
                } else if (simulator != null && delta > 0) {
                    simulator.advance(delta);
                    if (simulator.isFinished()) {
                        state = SimulationSnapshot.STATE_FINISHED;
                        updateFloatingWindow();
                    }
                }
            } else {
                lastTickElapsed = now;
            }

            RoutePath.Sample sample = currentSample();
            if (sample != null) {
                publishLocation(sample);
                publishSnapshot(sample);
                if (now - lastNotificationElapsed >= NOTIFICATION_INTERVAL_MS) {
                    lastNotificationElapsed = now;
                    updateNotification();
                }
            }

            long delay = state == SimulationSnapshot.STATE_RUNNING
                    ? RUNNING_INTERVAL_MS : HOLD_INTERVAL_MS;
            workerHandler.postDelayed(this, delay);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        workerThread = new HandlerThread("GoWithLineMockLocation", Process.THREAD_PRIORITY_FOREGROUND);
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
        errorStopRunnable = () -> workerHandler.post(() -> stopSimulation(true));
        floatingWindow = new FloatingSpeedWindow(this, new FloatingSpeedWindow.Listener() {
            @Override
            public void onSpeedSelected(double kmh) {
                setSpeedKmh(kmh);
            }

            @Override
            public void onJoystickMoved(double bearingDegrees, boolean active) {
                setJoystickDirection(bearingDegrees, active);
            }

            @Override
            public void onClose() {
                SimulationSnapshot current = snapshot;
                if (current != null && current.isActive()
                        && current.mode == SimulationSnapshot.MODE_JOYSTICK) {
                    // The overlay joystick is the only control surface in
                    // joystick mode; closing it must end the run instead of
                    // leaving a frozen position injected with no way back.
                    workerHandler.post(() -> stopSimulation(true));
                    Toast.makeText(MockLocationService.this,
                            R.string.toast_joystick_close_stops,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                setJoystickDirection(0.0, false);
                Prefs.get(MockLocationService.this).edit()
                        .putBoolean(Prefs.FLOATING_ENABLED, false).apply();
            }
        });

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "GoWithLine::MockLocation");
            wakeLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            try {
                startForegroundCompat(buildNotification(true));
            } catch (Exception e) {
                Log.e(TAG, "startForeground failed", e);
                handleError(getString(R.string.error_foreground_start));
                return START_NOT_STICKY;
            }
            Intent startIntent = new Intent(intent);
            workerHandler.post(() -> startSimulation(startIntent));
        } else if (ACTION_PAUSE.equals(action)) {
            workerHandler.post(this::pauseSimulation);
        } else if (ACTION_RESUME.equals(action)) {
            workerHandler.post(this::resumeSimulation);
        } else if (ACTION_STOP.equals(action)) {
            workerHandler.post(() -> stopSimulation(true));
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        listeners.clear();
        if (workerHandler != null) {
            workerHandler.removeCallbacksAndMessages(null);
        }
        cleanupProviders();
        releaseWakeLock();
        if (workerThread != null) {
            workerThread.quitSafely();
        }
        if (foregroundStarted) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } catch (Exception ignored) {
            }
            foregroundStarted = false;
        }
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        hideFloatingWindow();
        super.onDestroy();
    }

    // ---------------------------------------------------------------------
    // Binder API
    // ---------------------------------------------------------------------

    public void pause() {
        workerHandler.post(this::pauseSimulation);
    }

    public void resume() {
        workerHandler.post(this::resumeSimulation);
    }

    public void stopSimulation() {
        workerHandler.post(() -> stopSimulation(true));
    }

    public void setSpeedMps(double speedMps) {
        workerHandler.post(() -> {
            this.speedMps = clampSpeed(speedMps);
            if (simulator != null) {
                simulator.setSpeedMps(this.speedMps);
            }
            if (state == SimulationSnapshot.STATE_RUNNING
                    || state == SimulationSnapshot.STATE_PAUSED
                    || state == SimulationSnapshot.STATE_FINISHED) {
                RoutePath.Sample sample = currentSample();
                if (sample != null) {
                    publishSnapshot(sample);
                }
            }
            updateFloatingWindow();
            Prefs.get(this).edit()
                    .putFloat(Prefs.SPEED_KMH, (float) (this.speedMps * 3.6)).apply();
        });
    }

    public void setSpeedKmh(double kmh) {
        setSpeedMps(kmh / 3.6);
    }

    public void showFloatingWindow() {
        SimulationSnapshot current = snapshot;
        if (current == null || !current.isActive()) {
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            return;
        }
        if (mode != SimulationSnapshot.MODE_JOYSTICK
                && !Prefs.get(this).getBoolean(Prefs.FLOATING_ENABLED, false)) {
            return;
        }
        double kmh = speedMps * 3.6;
        int currentState = state;
        int currentMode = mode;
        mainHandler.post(() -> {
            if (floatingWindow != null) {
                floatingWindow.show(kmh, currentState, currentMode);
            }
        });
    }

    public void hideFloatingWindow() {
        if (floatingWindow != null) {
            floatingWindow.hide();
        }
    }

    public SimulationSnapshot getSnapshot() {
        return snapshot;
    }

    public ServiceDiagnostics getDiagnostics() {
        return diagnostics;
    }

    public boolean isSimulationActive() {
        SimulationSnapshot current = snapshot;
        return current != null && current.isActive();
    }

    public void addListener(Listener newListener) {
        if (newListener != null && listeners.add(newListener)) {
            SimulationSnapshot current = snapshot;
            mainHandler.post(() -> {
                if (listeners.contains(newListener)) {
                    newListener.onSnapshot(current);
                }
            });
        }
    }

    public void removeListener(Listener oldListener) {
        listeners.remove(oldListener);
    }

    // ---------------------------------------------------------------------
    // Simulation lifecycle (worker thread)
    // ---------------------------------------------------------------------

    private void startSimulation(Intent intent) {
        if (destroyed) {
            return;
        }
        mainHandler.removeCallbacks(errorStopRunnable);
        stopTickAndProviders();

        mode = intent.getIntExtra(EXTRA_MODE, SimulationSnapshot.MODE_ROUTE);
        speedMps = clampSpeed(intent.getDoubleExtra(EXTRA_SPEED_MPS, 1.4));
        loop = intent.getBooleanExtra(EXTRA_LOOP, false);

        if (mode == SimulationSnapshot.MODE_JOYSTICK) {
            double lat = intent.getDoubleExtra(EXTRA_START_LAT, Double.NaN);
            double lng = intent.getDoubleExtra(EXTRA_START_LNG, Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lng)
                    || lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
                handleError(getString(R.string.error_invalid_route));
                return;
            }

            simulator = null;
            joyLat = lat;
            joyLng = lng;
            joyBearing = 0.0f;
            joyActive = false;
            joyTravelledMeters = 0.0;

            if (!setupProviders()) {
                handleError(getString(R.string.error_mock_permission));
                return;
            }
            acquireWakeLock();
            Log.i(TAG, "joystick simulation started: lat=" + lat + " lng=" + lng
                    + " speedMps=" + speedMps
                    + " gps=" + gpsReady + " network=" + networkReady
                    + " fused=" + fusedReady);
            beginRun(currentSample());
            return;
        }

        double[] lats = intent.getDoubleArrayExtra(EXTRA_LATS);
        double[] lngs = intent.getDoubleArrayExtra(EXTRA_LNGS);
        if (lats == null || lngs == null || lats.length != lngs.length || lats.length < 2) {
            handleError(getString(R.string.error_invalid_route));
            return;
        }

        List<PointD> points = new ArrayList<>(lats.length);
        for (int i = 0; i < lats.length; i++) {
            points.add(new PointD(lats[i], lngs[i]));
        }

        RoutePath path;
        try {
            path = new RoutePath(points);
        } catch (Exception e) {
            handleError(getString(R.string.error_invalid_route));
            return;
        }
        if (!path.isUsable()) {
            handleError(getString(R.string.error_route_too_short));
            return;
        }

        if (!setupProviders()) {
            handleError(getString(R.string.error_mock_permission));
            return;
        }
        acquireWakeLock();

        simulator = new RouteSimulator(path, speedMps, loop);
        Log.i(TAG, "simulation started: points=" + points.size()
                + " totalMeters=" + path.getTotalMeters()
                + " speedMps=" + speedMps
                + " loop=" + loop
                + " gps=" + gpsReady + " network=" + networkReady
                + " fused=" + fusedReady);
        beginRun(simulator.getSample());
    }

    private void beginRun(RoutePath.Sample initialSample) {
        if (initialSample == null) {
            handleError(getString(R.string.error_invalid_route));
            return;
        }
        state = SimulationSnapshot.STATE_RUNNING;
        lastTickElapsed = SystemClock.elapsedRealtime();
        lastNotificationElapsed = 0L;
        injectedCount = 0;
        consecutiveInjectionFailures = 0;
        lastInjectionError = null;
        echoCount = 0;
        echoLat = Double.NaN;
        echoLng = Double.NaN;
        echoElapsedMs = 0L;
        injectedLat = Double.NaN;
        injectedLng = Double.NaN;
        updateDiagnostics();
        showFloatingWindowIfEnabled();
        publishLocation(initialSample);
        publishSnapshot(initialSample);
        updateNotification();

        workerHandler.removeCallbacks(tickRunnable);
        workerHandler.post(tickRunnable);
    }

    private RoutePath.Sample currentSample() {
        if (mode == SimulationSnapshot.MODE_JOYSTICK) {
            if (Double.isNaN(joyLat) || Double.isNaN(joyLng)) {
                return null;
            }
            return new RoutePath.Sample(new PointD(joyLat, joyLng), joyBearing, 0);
        }
        return simulator == null ? null : simulator.getSample();
    }

    public void setJoystickDirection(double bearingDegrees, boolean active) {
        workerHandler.post(() -> {
            if (mode != SimulationSnapshot.MODE_JOYSTICK
                    || state != SimulationSnapshot.STATE_RUNNING) {
                return;
            }
            joyBearing = (float) GeoMath.normalizeBearing(bearingDegrees);
            joyActive = active;
        });
    }

    private void pauseSimulation() {
        if (state != SimulationSnapshot.STATE_RUNNING) {
            if (state == SimulationSnapshot.STATE_IDLE) {
                stopSelf();
            }
            return;
        }
        if (mode != SimulationSnapshot.MODE_JOYSTICK && simulator == null) {
            return;
        }
        state = SimulationSnapshot.STATE_PAUSED;
        if (mode == SimulationSnapshot.MODE_JOYSTICK) {
            joyActive = false;
        } else {
            simulator.pause();
            Log.i(TAG, "simulation paused at " + simulator.getTravelledMeters() + "m");
        }
        republishRunState();
    }

    private void resumeSimulation() {
        if (state != SimulationSnapshot.STATE_PAUSED) {
            if (state == SimulationSnapshot.STATE_IDLE) {
                stopSelf();
            }
            return;
        }
        if (mode != SimulationSnapshot.MODE_JOYSTICK && simulator == null) {
            return;
        }
        state = SimulationSnapshot.STATE_RUNNING;
        if (mode == SimulationSnapshot.MODE_JOYSTICK) {
            joyActive = false;
        } else {
            simulator.resume();
            Log.i(TAG, "simulation resumed at " + simulator.getTravelledMeters() + "m");
        }
        republishRunState();
        // Re-arm the control surface: resuming from the notification must not
        // leave a joystick run without its overlay.
        showFloatingWindowIfEnabled();
    }

    /** Publishes the current sample and refreshes notification + overlay. */
    private void republishRunState() {
        lastTickElapsed = SystemClock.elapsedRealtime();
        RoutePath.Sample sample = currentSample();
        if (sample != null) {
            publishLocation(sample);
            publishSnapshot(sample);
        }
        updateNotification();
        updateFloatingWindow();
    }

    private void stopSimulation(boolean stopSelf) {
        Log.i(TAG, "simulation stopped");
        mainHandler.removeCallbacks(errorStopRunnable);
        stopTickAndProviders();
        state = SimulationSnapshot.STATE_IDLE;
        simulator = null;
        joyActive = false;
        joyTravelledMeters = 0.0;
        joyLat = Double.NaN;
        joyLng = Double.NaN;
        hideFloatingWindow();
        SimulationSnapshot idle = SimulationSnapshot.idle();
        snapshot = idle;
        updateDiagnostics();
        deliver(idle);

        if (foregroundStarted) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } catch (Exception ignored) {
            }
            foregroundStarted = false;
        }
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        if (stopSelf) {
            stopSelf();
        }
    }

    private void handleError(String message) {
        workerHandler.post(() -> {
            stopTickAndProviders();
            state = SimulationSnapshot.STATE_IDLE;
            simulator = null;
            joyActive = false;
            joyLat = Double.NaN;
            joyLng = Double.NaN;
            hideFloatingWindow();
            SimulationSnapshot error = new SimulationSnapshot(
                    SimulationSnapshot.STATE_ERROR, mode, Double.NaN, Double.NaN,
                    0.0, 0.0, 0.0, 0.0, 0.0, false, message, ++revision);
            snapshot = error;
            updateDiagnostics();
            deliver(error);

            notifySafely(buildErrorNotification(message));

            mainHandler.removeCallbacks(errorStopRunnable);
            mainHandler.postDelayed(errorStopRunnable, 5000L);
        });
    }

    private void stopTickAndProviders() {
        workerHandler.removeCallbacks(tickRunnable);
        cleanupProviders();
        releaseWakeLock();
    }

    // ---------------------------------------------------------------------
    // Test providers
    // ---------------------------------------------------------------------

    private boolean setupProviders() {
        if (locationManager == null) {
            return false;
        }
        gpsError = null;
        networkError = null;
        fusedError = null;

        gpsReady = addProvider(LocationManager.GPS_PROVIDER,
                false, true, false, false, true, true, true, true);
        if (!gpsReady) {
            gpsError = lastProviderError;
        }

        networkReady = addProvider(LocationManager.NETWORK_PROVIDER,
                true, false, true, true, true, true, true, false);
        if (!networkReady) {
            networkError = lastProviderError;
        }

        // Many apps query the framework's fused provider. AOSP allows a test
        // provider named "fused"; Play Services' own fused implementation may
        // still ignore it, but this improves compatibility where possible.
        fusedReady = addProvider(FUSED_PROVIDER_NAME,
                false, false, false, false, true, true, true, true);
        if (!fusedReady) {
            fusedError = lastProviderError;
        }

        boolean any = gpsReady || networkReady || fusedReady;
        if (any) {
            startEchoListener();
        }
        updateDiagnostics();
        return any;
    }

    private boolean addProvider(String name,
                                boolean requiresNetwork, boolean requiresSatellite,
                                boolean requiresCell, boolean hasMonetaryCost,
                                boolean supportsAltitude, boolean supportsSpeed,
                                boolean supportsBearing, boolean fine) {
        lastProviderError = null;
        MockLocationHelper.removeTestProvider(locationManager, name);
        try {
            try {
                MockLocationHelper.addTestProvider(locationManager, name,
                        requiresNetwork, requiresSatellite, requiresCell, hasMonetaryCost,
                        supportsAltitude, supportsSpeed, supportsBearing, fine);
            } catch (IllegalArgumentException conflict) {
                // Some vendor ROMs keep a stale provider entry around; remove
                // and retry once before giving up.
                MockLocationHelper.removeTestProvider(locationManager, name);
                MockLocationHelper.addTestProvider(locationManager, name,
                        requiresNetwork, requiresSatellite, requiresCell, hasMonetaryCost,
                        supportsAltitude, supportsSpeed, supportsBearing, fine);
            }
            locationManager.setTestProviderEnabled(name, true);
            return true;
        } catch (Exception e) {
            lastProviderError = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
            Log.e(TAG, "cannot add test provider " + name, e);
            return false;
        }
    }

    private void cleanupProviders() {
        stopEchoListener();
        if (locationManager == null) {
            return;
        }
        // Always attempt removal, even for a provider whose "ready" flag is
        // false: setTestProviderEnabled() may have failed after the provider
        // was registered, and a stale test provider must not be left behind.
        MockLocationHelper.removeTestProvider(locationManager, LocationManager.GPS_PROVIDER);
        MockLocationHelper.removeTestProvider(locationManager, LocationManager.NETWORK_PROVIDER);
        MockLocationHelper.removeTestProvider(locationManager, FUSED_PROVIDER_NAME);
        gpsReady = false;
        networkReady = false;
        fusedReady = false;
    }

    private void publishLocation(RoutePath.Sample sample) {
        injectedLat = sample.point.lat;
        injectedLng = sample.point.lng;
        boolean moving = state == SimulationSnapshot.STATE_RUNNING
                && (mode != SimulationSnapshot.MODE_JOYSTICK || joyActive);
        float locationSpeed = moving ? (float) speedMps : 0.0f;

        boolean any = false;
        any |= setTestLocation(LocationManager.GPS_PROVIDER, sample,
                locationSpeed, 3.0f, true, gpsReady);
        any |= setTestLocation(LocationManager.NETWORK_PROVIDER, sample,
                locationSpeed, 25.0f, false, networkReady);
        any |= setTestLocation(FUSED_PROVIDER_NAME, sample,
                locationSpeed, 5.0f, false, fusedReady);

        if (any) {
            injectedCount++;
            consecutiveInjectionFailures = 0;
        } else if (gpsReady || networkReady || fusedReady) {
            consecutiveInjectionFailures++;
            if (consecutiveInjectionFailures >= 5) {
                String detail = lastInjectionError == null ? "" : ": " + lastInjectionError;
                handleError("位置注入失败" + detail);
                return;
            }
        }
        updateDiagnostics();
    }

    private boolean setTestLocation(String provider, RoutePath.Sample sample,
                                    float speed, float accuracy, boolean gps,
                                    boolean providerReady) {
        if (!providerReady || locationManager == null) {
            return false;
        }
        try {
            Location location = new Location(provider);
            location.setAccuracy(accuracy);
            location.setAltitude(ALTITUDE_METERS);
            location.setBearing((float) sample.bearingDegrees);
            location.setLatitude(sample.point.lat);
            location.setLongitude(sample.point.lng);
            location.setTime(System.currentTimeMillis());
            location.setSpeed(speed);
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            location.setVerticalAccuracyMeters(accuracy);
            location.setSpeedAccuracyMetersPerSecond(0.5f);
            location.setBearingAccuracyDegrees(5.0f);
            if (gps) {
                Bundle extras = new Bundle();
                extras.putInt("satellites", 8);
                location.setExtras(extras);
            }
            locationManager.setTestProviderLocation(provider, location);
            return true;
        } catch (Exception e) {
            lastInjectionError = provider + ": " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
            Log.w(TAG, "setTestProviderLocation failed for " + provider, e);
            return false;
        }
    }

    /**
     * Registers a location listener so the self-check can verify that the
     * platform actually echoes our injected coordinates back. Without this,
     * the UI marker still moves (it is driven by the simulator) even when
     * provider injection is silently rejected.
     */
    @SuppressLint("MissingPermission")
    private void startEchoListener() {
        stopEchoListener();
        if (locationManager == null) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        echoListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location == null || Double.isNaN(injectedLat)) {
                    return;
                }
                if (EchoMatcher.isEcho(injectedLat, injectedLng,
                        location.getLatitude(), location.getLongitude(),
                        LocationCompat.isMock(location))) {
                    echoCount++;
                    echoLat = location.getLatitude();
                    echoLng = location.getLongitude();
                    echoElapsedMs = SystemClock.elapsedRealtime();
                    updateDiagnostics();
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        try {
            if (gpsReady) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        200L, 0f, echoListener, workerThread.getLooper());
            }
            if (networkReady) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        200L, 0f, echoListener, workerThread.getLooper());
            }
            if (fusedReady) {
                locationManager.requestLocationUpdates(FUSED_PROVIDER_NAME,
                        200L, 0f, echoListener, workerThread.getLooper());
            }
        } catch (Exception e) {
            Log.w(TAG, "cannot register echo location listener", e);
        }
    }

    private void stopEchoListener() {
        if (echoListener != null && locationManager != null) {
            try {
                locationManager.removeUpdates(echoListener);
            } catch (Exception ignored) {
            }
        }
        echoListener = null;
    }

    private void updateDiagnostics() {
        diagnostics = new ServiceDiagnostics(
                state == SimulationSnapshot.STATE_RUNNING
                        || state == SimulationSnapshot.STATE_PAUSED
                        || state == SimulationSnapshot.STATE_FINISHED,
                gpsReady, networkReady, fusedReady,
                gpsError, networkError, fusedError,
                injectedCount, lastInjectionError,
                echoCount, echoLat, echoLng,
                injectedLat, injectedLng, echoElapsedMs);
    }

    private void showFloatingWindowIfEnabled() {
        if (floatingWindow == null) {
            return;
        }
        boolean enabled = mode == SimulationSnapshot.MODE_JOYSTICK
                || Prefs.get(this).getBoolean(Prefs.FLOATING_ENABLED, false);
        if (!enabled || !Settings.canDrawOverlays(this)) {
            return;
        }
        double kmh = speedMps * 3.6;
        int currentState = state;
        int currentMode = mode;
        mainHandler.post(() -> {
            if (floatingWindow != null) {
                floatingWindow.show(kmh, currentState, currentMode);
            }
        });
    }

    private void updateFloatingWindow() {
        FloatingSpeedWindow window = floatingWindow;
        if (window == null) {
            return;
        }
        window.update(speedMps * 3.6, state, mode);
    }

    // ---------------------------------------------------------------------
    // Snapshot + notification
    // ---------------------------------------------------------------------

    private void publishSnapshot(RoutePath.Sample sample) {
        if (sample == null) {
            return;
        }
        double travelled;
        double total;
        double progress;
        if (mode == SimulationSnapshot.MODE_JOYSTICK) {
            travelled = joyTravelledMeters;
            total = 0.0;
            progress = 0.0;
        } else {
            if (simulator == null) {
                return;
            }
            travelled = simulator.getTravelledMeters();
            total = simulator.getTotalMeters();
            progress = simulator.getProgress();
        }
        SimulationSnapshot current = new SimulationSnapshot(
                state,
                mode,
                sample.point.lat,
                sample.point.lng,
                sample.bearingDegrees,
                speedMps,
                travelled,
                total,
                progress,
                loop,
                null,
                ++revision);
        snapshot = current;
        deliver(current);
    }

    private void deliver(SimulationSnapshot current) {
        for (Listener target : listeners) {
            mainHandler.post(() -> {
                if (listeners.contains(target)) {
                    target.onSnapshot(current);
                }
            });
        }
    }

    private void createNotificationChannel() {
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_channel_description));
        channel.setSound(null, null);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        foregroundStarted = true;
    }

    private void updateNotification() {
        if (state == SimulationSnapshot.STATE_IDLE
                || state == SimulationSnapshot.STATE_ERROR) {
            return;
        }
        notifySafely(buildNotification(false));
    }

    @SuppressLint("MissingPermission")
    private void notifySafely(Notification notification) {
        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(this);
            if (manager.areNotificationsEnabled()) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.w(TAG, "notification failed", e);
        }
    }

    private Notification buildNotification(boolean starting) {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(buildNotificationText(starting))
                        .setContentIntent(contentPendingIntent())
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setSilent(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE);

        if (!starting) {
            if (state == SimulationSnapshot.STATE_RUNNING) {
                builder.addAction(0, getString(R.string.notification_pause),
                        servicePendingIntent(ACTION_PAUSE, 1));
            } else if (state == SimulationSnapshot.STATE_PAUSED) {
                builder.addAction(0, getString(R.string.notification_resume),
                        servicePendingIntent(ACTION_RESUME, 2));
            }
            if (state == SimulationSnapshot.STATE_RUNNING
                    || state == SimulationSnapshot.STATE_PAUSED
                    || state == SimulationSnapshot.STATE_FINISHED) {
                builder.addAction(0, getString(R.string.notification_stop),
                        servicePendingIntent(ACTION_STOP, 3));
            }
        }
        return builder.build();
    }

    private Notification buildErrorNotification(String message) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_error_title))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(contentPendingIntent())
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    private String buildNotificationText(boolean starting) {
        if (starting) {
            return getString(R.string.notification_starting);
        }
        if (mode == SimulationSnapshot.MODE_JOYSTICK) {
            String joystickTravelled =
                    FormatUtils.formatDistance(joyTravelledMeters);
            switch (state) {
                case SimulationSnapshot.STATE_RUNNING:
                    return getString(R.string.notification_joystick_running,
                            speedMps * 3.6, joystickTravelled);
                case SimulationSnapshot.STATE_PAUSED:
                    return getString(R.string.notification_joystick_paused,
                            joystickTravelled);
                default:
                    return getString(R.string.notification_starting);
            }
        }
        if (simulator == null) {
            return getString(R.string.notification_starting);
        }
        String travelled = FormatUtils.formatDistance(simulator.getTravelledMeters());
        String total = FormatUtils.formatDistance(simulator.getTotalMeters());
        switch (state) {
            case SimulationSnapshot.STATE_RUNNING:
                return getString(R.string.notification_running,
                        speedMps * 3.6, travelled, total);
            case SimulationSnapshot.STATE_PAUSED:
                return getString(R.string.notification_paused, travelled, total);
            case SimulationSnapshot.STATE_FINISHED:
                return getString(R.string.notification_finished, total);
            default:
                return getString(R.string.notification_starting);
        }
    }

    private PendingIntent contentPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent servicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MockLocationService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ---------------------------------------------------------------------
    // Wake lock + helpers
    // ---------------------------------------------------------------------

    @SuppressLint("WakelockTimeout")
    private void acquireWakeLock() {
        if (wakeLock == null) {
            return;
        }
        try {
            if (!wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        } catch (Exception e) {
            Log.w(TAG, "wake lock acquire failed", e);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock == null) {
            return;
        }
        try {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
            Log.w(TAG, "wake lock release failed", e);
        }
    }

    private static double clampSpeed(double speedMps) {
        if (Double.isNaN(speedMps) || speedMps < MIN_SPEED_MPS) {
            return MIN_SPEED_MPS;
        }
        return Math.min(MAX_SPEED_MPS, speedMps);
    }
}
