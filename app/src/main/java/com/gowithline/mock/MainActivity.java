package com.gowithline.mock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.gowithline.mock.geo.CoordConverter;
import com.gowithline.mock.geo.GeoMath;
import com.gowithline.mock.geo.PointD;
import com.gowithline.mock.map.MapLayer;
import com.gowithline.mock.map.TileSources;
import com.gowithline.mock.service.MockLocationService;
import com.gowithline.mock.service.ServiceDiagnostics;
import com.gowithline.mock.service.SimulationSnapshot;
import com.gowithline.mock.ui.DrawOverlayView;
import com.gowithline.mock.util.DeviceState;
import com.gowithline.mock.util.FormatUtils;
import com.gowithline.mock.util.MockLocationHelper;
import com.gowithline.mock.util.Prefs;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayWithIW;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS = 42;
    private static final double MIN_ROUTE_METERS = 2.0;
    private static final double DEFAULT_CENTER_LAT = 35.8617;   // China overview
    private static final double DEFAULT_CENTER_LNG = 104.1954;

    private SharedPreferences prefs;
    private MapView mapView;
    private DrawOverlayView drawOverlay;
    private MaterialButton btnDraw;
    private MaterialButton btnUndo;
    private MaterialButton btnClear;
    private MaterialButton btnStart;
    private MaterialButton btnPause;
    private MaterialButton btnStop;
    private ImageButton btnSettings;
    private ImageButton btnHelp;
    private TextInputEditText editSpeed;
    private TextInputLayout speedLayout;
    private MaterialCheckBox checkLoop;
    private TextView textRouteInfo;
    private TextView textMockStatus;
    private TextView textAttribution;
    private TextView textMode;
    private View routeEditRow;
    private FloatingActionButton fabLocate;
    private FloatingActionButton fabFollow;

    private Polyline routePolyline;
    private Marker startMarker;
    private Marker endMarker;
    private Marker currentMarker;

    private MapLayer currentLayer;
    private String customTileUrl;
    private MapLayer.CoordSystem customTileCoord = MapLayer.CoordSystem.WGS84;

    private final List<List<GeoPoint>> routeStrokes = new ArrayList<>();
    private final List<GeoPoint> routePoints = new ArrayList<>();
    private double routeDistanceMeters;

    private boolean drawMode;
    private boolean follow;
    private boolean starting;
    private boolean settingSpeed;
    private boolean joystickMode;
    private PointD joystickStartWgs;
    private boolean bound;
    private boolean bindingRequested;
    private MockLocationService service;
    private SimulationSnapshot lastSnapshot = SimulationSnapshot.idle();
    private String lastErrorShown;
    private long lastTextUpdateElapsed;
    private long lastMockStatusElapsed;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final MockLocationService.Listener snapshotListener = this::applySnapshot;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((MockLocationService.LocalBinder) binder).getService();
            bound = true;
            syncServiceAndSettings();
            service.addListener(snapshotListener);
            // The service itself gates on mode (joystick always shows) and on
            // the route-mode preference, so this is a safe re-arm for runs
            // whose overlay was closed earlier.
            service.showFloatingWindow();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        joystickMode = Prefs.MODE_JOYSTICK.equals(
                prefs.getString(Prefs.SIMULATION_MODE, Prefs.MODE_ROUTE));
        configureOsmdroid();
        currentLayer = restoreLayer();
        restoreJoystickStart();

        setContentView(R.layout.activity_main);
        bindViews();
        initMap();
        initOverlays();
        initListeners();
        restoreRoute();
        restoreUiSettings();
        updateRouteOverlays();
        updateRouteInfo();
        updateButtons();
        applyModeUi();
        showDisclaimerIfNeeded();
        maybeAutoStartForDebug();
    }

    /**
     * Debug-only hook used by adb during development:
     * am start -n com.gowithline.mock/.MainActivity --ez auto_start true
     * It starts the saved route without needing touch injection (which some
     * HyperOS builds block unless "USB debugging (Security settings)" is on).
     */
    private void maybeAutoStartForDebug() {
        boolean debuggable =
                (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debuggable) {
            return;
        }
        if (getIntent().getBooleanExtra("floating", false)) {
            prefs.edit().putBoolean(Prefs.FLOATING_ENABLED, true).apply();
        }
        if (getIntent().hasExtra("debug_speed")) {
            Bundle extras = getIntent().getExtras();
            Object value = extras == null ? null : extras.get("debug_speed");
            if (value instanceof Number) {
                double kmh = ((Number) value).doubleValue();
                uiHandler.postDelayed(() -> {
                    if (service != null) {
                        service.setSpeedKmh(kmh);
                    }
                }, 4500L);
            }
        }
        if (getIntent().hasExtra("debug_lat") && getIntent().hasExtra("debug_lng")) {
            Bundle extras = getIntent().getExtras();
            Object latValue = extras == null ? null : extras.get("debug_lat");
            Object lngValue = extras == null ? null : extras.get("debug_lng");
            double lat = latValue instanceof Number
                    ? ((Number) latValue).doubleValue() : Double.NaN;
            double lng = lngValue instanceof Number
                    ? ((Number) lngValue).doubleValue() : Double.NaN;
            if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                uiHandler.postDelayed(() -> startDebugRoute(lat, lng), 1200L);
                return;
            }
        }
        if (!getIntent().getBooleanExtra("auto_start", false)) {
            return;
        }
        uiHandler.postDelayed(() -> {
            if (!lastSnapshot.isActive()) {
                startSimulation();
            }
        }, 1200L);
    }

    /** Debug-only: inject a short looping WGS-84 route without touching the saved route. */
    private void startDebugRoute(double lat, double lng) {
        double[] lats = new double[]{lat, lat + 0.001};
        double[] lngs = new double[]{lng, lng};
        Intent intent = new Intent(this, MockLocationService.class);
        intent.setAction(MockLocationService.ACTION_START);
        intent.putExtra(MockLocationService.EXTRA_LATS, lats);
        intent.putExtra(MockLocationService.EXTRA_LNGS, lngs);
        intent.putExtra(MockLocationService.EXTRA_SPEED_MPS, 2.78);
        intent.putExtra(MockLocationService.EXTRA_LOOP, true);
        try {
            ContextCompat.startForegroundService(this, intent);
        } catch (Exception e) {
            showErrorDialog(getString(R.string.error_foreground_start));
        }
    }

    // ---------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------

    private void configureOsmdroid() {
        File basePath = new File(getFilesDir(), "osmdroid");
        File tileCache = new File(getCacheDir(), "osmdroid-tiles");
        //noinspection ResultOfMethodCallIgnored
        basePath.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        tileCache.mkdirs();

        Configuration.getInstance().setOsmdroidBasePath(basePath);
        Configuration.getInstance().setOsmdroidTileCache(tileCache);
        Configuration.getInstance().setUserAgentValue(
                "GoWithLine/" + BuildConfig.VERSION_NAME
                        + " (+https://github.com/99galaxy/GoWithLine)");
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(150L * 1024L * 1024L);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(120L * 1024L * 1024L);
    }

    private MapLayer restoreLayer() {
        String id = prefs.getString(Prefs.LAYER_ID, MapLayer.ID_OSM);
        if (!MapLayer.ID_OSM.equals(id) && !MapLayer.ID_CUSTOM.equals(id)) {
            // Built-in "gaode"/"esri" layers were removed; migrate to OSM.
            id = MapLayer.ID_OSM;
            prefs.edit().putString(Prefs.LAYER_ID, MapLayer.ID_OSM).apply();
        }
        customTileUrl = prefs.getString(Prefs.CUSTOM_URL, null);
        String coordName = prefs.getString(
                Prefs.CUSTOM_COORD, MapLayer.CoordSystem.WGS84.name());
        try {
            customTileCoord = MapLayer.CoordSystem.valueOf(coordName);
        } catch (IllegalArgumentException ignored) {
            customTileCoord = MapLayer.CoordSystem.WGS84;
        }

        if (MapLayer.ID_CUSTOM.equals(id) && TileSources.isValidTemplate(customTileUrl)) {
            try {
                return TileSources.custom(customTileUrl, customTileCoord);
            } catch (IllegalArgumentException ignored) {
                return TileSources.osm();
            }
        }
        return TileSources.builtIn(id);
    }

    private void bindViews() {
        mapView = findViewById(R.id.mapView);
        drawOverlay = findViewById(R.id.drawOverlay);
        btnDraw = findViewById(R.id.btnDraw);
        btnUndo = findViewById(R.id.btnUndo);
        btnClear = findViewById(R.id.btnClear);
        btnStart = findViewById(R.id.btnStart);
        btnPause = findViewById(R.id.btnPause);
        btnStop = findViewById(R.id.btnStop);
        btnSettings = findViewById(R.id.btnSettings);
        btnHelp = findViewById(R.id.btnHelp);
        editSpeed = findViewById(R.id.editSpeed);
        speedLayout = findViewById(R.id.speedLayout);
        checkLoop = findViewById(R.id.checkLoop);
        textRouteInfo = findViewById(R.id.textRouteInfo);
        textMockStatus = findViewById(R.id.textMockStatus);
        textAttribution = findViewById(R.id.textAttribution);
        textMode = findViewById(R.id.textMode);
        routeEditRow = findViewById(R.id.routeEditRow);
        fabLocate = findViewById(R.id.fabLocate);
        fabFollow = findViewById(R.id.fabFollow);
    }

    private void initMap() {
        mapView.setTileSource(currentLayer.tileSource);
        mapView.setMultiTouchControls(true);
        mapView.setTilesScaledToDpi(true);
        mapView.setUseDataConnection(true);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setMinZoomLevel(2.0);
        mapView.setMaxZoomLevel(19.0);

        float zoom = prefs.getFloat(Prefs.ZOOM, Float.NaN);
        GeoPoint center = restoredCenter();
        if (Float.isNaN(zoom)) {
            zoom = DeviceState.hasFineLocationPermission(this) ? 16.0f : 4.0f;
        }
        mapView.getController().setZoom(zoom);
        mapView.getController().setCenter(center);
        textAttribution.setText(currentLayer.attribution);
    }

    private GeoPoint restoredCenter() {
        String latText = prefs.getString(Prefs.CENTER_LAT_WGS, null);
        String lngText = prefs.getString(Prefs.CENTER_LNG_WGS, null);
        if (latText != null && lngText != null) {
            try {
                return toMapGeoPoint(new PointD(
                        Double.parseDouble(latText), Double.parseDouble(lngText)));
            } catch (NumberFormatException ignored) {
                // Fall through to a live/default center.
            }
        }

        Location location = getLastKnownLocation();
        if (location != null) {
            return toMapGeoPoint(new PointD(location.getLatitude(), location.getLongitude()));
        }
        return toMapGeoPoint(new PointD(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LNG));
    }

    private void initOverlays() {
        mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                return selectJoystickStart(point);
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        }));
        routePolyline = new Polyline(mapView);
        routePolyline.setColor(getColor(R.color.route_blue));
        routePolyline.setWidth(dp(6.0f));
        routePolyline.setGeodesic(false);
        mapView.getOverlays().add(routePolyline);

        startMarker = createMarker(R.drawable.ic_start_marker);
        startMarker.setOnMarkerClickListener((clicked, view) -> !joystickMode);
        endMarker = createMarker(R.drawable.ic_end_marker);
        currentMarker = createMarker(R.drawable.ic_current_marker);
        currentMarker.setFlat(true);

        mapView.getOverlays().add(startMarker);
        mapView.getOverlays().add(endMarker);
        mapView.getOverlays().add(currentMarker);

        drawOverlay.setMapView(mapView);
        drawOverlay.setListener(points -> {
            if (points == null || points.isEmpty()) {
                return;
            }
            routeStrokes.add(new ArrayList<>(points));
            rebuildRoute();
        });
    }

    private Marker createMarker(int drawableRes) {
        Marker marker = new Marker(mapView);
        marker.setIcon(ContextCompat.getDrawable(this, drawableRes));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setInfoWindow(null);
        marker.setOnMarkerClickListener((clicked, view) -> true);
        marker.setVisible(false);
        return marker;
    }

    private void restoreJoystickStart() {
        String lat = prefs.getString(Prefs.JOYSTICK_START_LAT_WGS, null);
        String lng = prefs.getString(Prefs.JOYSTICK_START_LNG_WGS, null);
        if (lat == null || lng == null) {
            return;
        }
        try {
            double latitude = Double.parseDouble(lat);
            double longitude = Double.parseDouble(lng);
            if (Double.isFinite(latitude) && Double.isFinite(longitude)
                    && latitude >= -90.0 && latitude <= 90.0
                    && longitude >= -180.0 && longitude <= 180.0) {
                joystickStartWgs = new PointD(latitude, longitude);
            }
        } catch (NumberFormatException ignored) {
            // Invalid stored coordinates fall back to the map center.
        }
    }

    private boolean selectJoystickStart(GeoPoint point) {
        if (!joystickMode || starting || !bound || lastSnapshot.isActive()
                || (service != null && service.isSimulationActive())) {
            return false;
        }
        joystickStartWgs = toWgsPoint(new PointD(point.getLatitude(), point.getLongitude()));
        prefs.edit()
                .putString(Prefs.JOYSTICK_START_LAT_WGS, Double.toString(joystickStartWgs.lat))
                .putString(Prefs.JOYSTICK_START_LNG_WGS, Double.toString(joystickStartWgs.lng))
                .apply();
        updateRouteOverlays();
        updateRouteInfo();
        return true;
    }

    private void initListeners() {
        btnSettings.setOnClickListener(v -> openSettings());
        btnHelp.setOnClickListener(v -> showHelpDialog());
        btnDraw.setOnClickListener(v -> setDrawMode(!drawMode));
        btnUndo.setOnClickListener(v -> undoStroke());
        btnClear.setOnClickListener(v -> confirmClearRoute());
        btnStart.setOnClickListener(v -> startSimulation());
        btnPause.setOnClickListener(v -> togglePause());
        btnStop.setOnClickListener(v -> stopSimulation());

        findViewById(R.id.btnPresetWalk).setOnClickListener(v -> setSpeedKmh(5.0));
        findViewById(R.id.btnPresetRun).setOnClickListener(v -> setSpeedKmh(10.0));
        findViewById(R.id.btnPresetBike).setOnClickListener(v -> setSpeedKmh(20.0));
        findViewById(R.id.btnPresetDrive).setOnClickListener(v -> setSpeedKmh(40.0));

        editSpeed.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onSpeedTextChanged();
            }
        });
        editSpeed.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                editSpeed.clearFocus();
                hideKeyboard();
                return true;
            }
            return false;
        });

        checkLoop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(Prefs.LOOP, isChecked).apply();
        });

        fabLocate.setOnClickListener(v -> locateMe());
        fabFollow.setOnClickListener(v -> toggleFollow());
        textMockStatus.setOnClickListener(v -> handleMockStatusClick());
    }

    private void restoreUiSettings() {
        float speed = prefs.getFloat(Prefs.SPEED_KMH, 5.0f);
        setSpeedKmh(speed);
        boolean loop = prefs.getBoolean(Prefs.LOOP, false);
        checkLoop.setChecked(loop);
        follow = prefs.getBoolean(Prefs.FOLLOW, false);
        updateFollowUi();
        textAttribution.setText(currentLayer.attribution);
    }

    // ---------------------------------------------------------------------
    // Route model
    // ---------------------------------------------------------------------

    private void restoreRoute() {
        String raw = prefs.getString(Prefs.ROUTE, "");
        if (raw == null || raw.isEmpty()) {
            return;
        }

        MapLayer.CoordSystem storedSystem;
        try {
            storedSystem = MapLayer.CoordSystem.valueOf(
                    prefs.getString(Prefs.ROUTE_COORD, currentLayer.coordSystem.name()));
        } catch (IllegalArgumentException ignored) {
            storedSystem = currentLayer.coordSystem;
        }

        String[] strokeStrings = raw.split("\\|");
        for (String strokeString : strokeStrings) {
            if (strokeString.isEmpty()) {
                continue;
            }
            List<GeoPoint> stroke = new ArrayList<>();
            for (String pointString : strokeString.split(";")) {
                String[] parts = pointString.split(",");
                if (parts.length != 2) {
                    continue;
                }
                try {
                    PointD stored = new PointD(
                            Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                    PointD converted = convertCoord(
                            stored, storedSystem, currentLayer.coordSystem);
                    stroke.add(new GeoPoint(converted.lat, converted.lng));
                } catch (NumberFormatException ignored) {
                    // Ignore a single malformed point.
                }
            }
            if (!stroke.isEmpty()) {
                routeStrokes.add(stroke);
            }
        }
        rebuildRoute();
    }

    private void rebuildRoute() {
        routePoints.clear();
        for (List<GeoPoint> stroke : routeStrokes) {
            routePoints.addAll(stroke);
        }
        routeDistanceMeters = computeDistance(routePoints);
        updateRouteOverlays();
        updateRouteInfo();
        updateButtons();
        saveRoute();
    }

    private double computeDistance(List<GeoPoint> points) {
        double total = 0.0;
        for (int i = 1; i < points.size(); i++) {
            GeoPoint a = points.get(i - 1);
            GeoPoint b = points.get(i);
            total += GeoMath.distanceMeters(new PointD(a.getLatitude(), a.getLongitude()),
                    new PointD(b.getLatitude(), b.getLongitude()));
        }
        return total;
    }

    private void updateRouteOverlays() {
        boolean showRoute = !joystickMode;
        routePolyline.setPoints(new ArrayList<>(routePoints));
        routePolyline.setVisible(showRoute);
        boolean hasPoints = !routePoints.isEmpty();
        if (showRoute && hasPoints) {
            startMarker.setPosition(routePoints.get(0));
            endMarker.setPosition(routePoints.get(routePoints.size() - 1));
        } else if (joystickMode && joystickStartWgs != null) {
            startMarker.setPosition(toMapGeoPoint(joystickStartWgs));
        }
        boolean showJoystickStart = joystickMode && joystickStartWgs != null
                && !starting && !lastSnapshot.isActive();
        startMarker.setVisible((showRoute && hasPoints) || showJoystickStart);
        endMarker.setVisible(showRoute && hasPoints);
        closeInfoWindows();
        mapView.invalidate();
    }

    private void closeInfoWindows() {
        if (mapView == null) {
            return;
        }
        for (Overlay overlay : mapView.getOverlays()) {
            if (overlay instanceof OverlayWithIW) {
                ((OverlayWithIW) overlay).closeInfoWindow();
            }
        }
    }

    private void updateRouteInfo() {
        if (joystickMode) {
            if (lastSnapshot.isActive()) {
                showActiveInfo(lastSnapshot);
            } else if (joystickStartWgs != null) {
                textRouteInfo.setText(getString(R.string.route_joystick_start_selected,
                        joystickStartWgs.lat, joystickStartWgs.lng));
            } else {
                textRouteInfo.setText(R.string.route_joystick_hint);
            }
            return;
        }
        if (lastSnapshot.isActive()) {
            showActiveInfo(lastSnapshot);
        } else if (routePoints.isEmpty()) {
            textRouteInfo.setText(R.string.route_empty);
        } else {
            double kmh = parseSpeedKmh();
            String distance = FormatUtils.formatDistance(routeDistanceMeters);
            if (kmh > 0) {
                double seconds = routeDistanceMeters / (kmh / 3.6);
                textRouteInfo.setText(getString(R.string.route_info,
                        distance, FormatUtils.formatDuration(seconds)));
            } else {
                textRouteInfo.setText(distance);
            }
        }
    }

    private void showActiveInfo(SimulationSnapshot snapshot) {
        if (snapshot.mode == SimulationSnapshot.MODE_JOYSTICK) {
            String moved = FormatUtils.formatDistance(snapshot.travelledMeters);
            switch (snapshot.state) {
                case SimulationSnapshot.STATE_RUNNING:
                    textRouteInfo.setText(getString(
                            R.string.status_joystick_running, moved));
                    break;
                case SimulationSnapshot.STATE_PAUSED:
                    textRouteInfo.setText(getString(
                            R.string.status_joystick_paused, moved));
                    break;
                case SimulationSnapshot.STATE_FINISHED:
                    textRouteInfo.setText(getString(
                            R.string.status_joystick_finished, moved));
                    break;
                default:
                    textRouteInfo.setText(R.string.route_joystick_hint);
                    break;
            }
            return;
        }
        String travelled = FormatUtils.formatDistance(snapshot.travelledMeters);
        String total = FormatUtils.formatDistance(snapshot.totalMeters);
        switch (snapshot.state) {
            case SimulationSnapshot.STATE_RUNNING: {
                double remaining = Math.max(
                        0.0, snapshot.totalMeters - snapshot.travelledMeters);
                double eta = snapshot.speedMps > 0.01
                        ? remaining / snapshot.speedMps : 0.0;
                textRouteInfo.setText(getString(R.string.status_running_detail,
                        travelled, total, FormatUtils.formatDuration(eta)));
                break;
            }
            case SimulationSnapshot.STATE_PAUSED:
                textRouteInfo.setText(getString(R.string.status_paused_detail,
                        travelled, total));
                break;
            case SimulationSnapshot.STATE_FINISHED:
                textRouteInfo.setText(getString(R.string.status_finished_detail, total));
                break;
            default:
                textRouteInfo.setText(R.string.route_empty);
                break;
        }
    }

    private void updateButtons() {
        SimulationSnapshot snapshot = lastSnapshot;
        boolean active = snapshot.isActive();
        boolean hasRoute = routePoints.size() >= 2
                && routeDistanceMeters >= MIN_ROUTE_METERS;
        boolean canStart = joystickMode || hasRoute;
        boolean validSpeed = parseSpeedKmh() > 0;

        btnStart.setEnabled(snapshot.canStart() && !starting && canStart && validSpeed);
        if (starting) {
            btnStart.setText(R.string.btn_starting);
        } else {
            btnStart.setText(snapshot.state == SimulationSnapshot.STATE_FINISHED
                    ? R.string.btn_restart : R.string.btn_start);
        }

        btnPause.setEnabled(active && snapshot.state != SimulationSnapshot.STATE_FINISHED);
        btnPause.setText(snapshot.state == SimulationSnapshot.STATE_PAUSED
                ? R.string.btn_resume : R.string.btn_pause);
        btnStop.setEnabled(active);

        btnDraw.setEnabled(!active && !joystickMode);
        btnUndo.setEnabled(!active && !joystickMode && !routeStrokes.isEmpty());
        btnClear.setEnabled(!active && !joystickMode && !routePoints.isEmpty());
        checkLoop.setEnabled(!active && !joystickMode);
    }

    private void applyModeUi() {
        if (textMode != null) {
            textMode.setText(joystickMode
                    ? R.string.settings_mode_joystick
                    : R.string.settings_mode_route);
        }
        if (routeEditRow != null) {
            routeEditRow.setVisibility(joystickMode ? View.GONE : View.VISIBLE);
        }
        checkLoop.setVisibility(joystickMode ? View.GONE : View.VISIBLE);
        updateRouteOverlays();
        updateRouteInfo();
        updateButtons();
    }

    private void syncModeFromPrefs() {
        boolean desired = Prefs.MODE_JOYSTICK.equals(
                prefs.getString(Prefs.SIMULATION_MODE, Prefs.MODE_ROUTE));
        if (desired == joystickMode) {
            return;
        }
        if (lastSnapshot.isActive()) {
            // Settings disables the switch while running; keep the current UI
            // and preference consistent if it was changed externally.
            prefs.edit().putString(Prefs.SIMULATION_MODE,
                    joystickMode ? Prefs.MODE_JOYSTICK : Prefs.MODE_ROUTE).apply();
            toast(R.string.settings_mode_locked);
            return;
        }
        joystickMode = desired;
        applyModeUi();
    }

    private void syncLayerFromPrefs() {
        if (currentLayer == null) {
            return;
        }
        String id = prefs.getString(Prefs.LAYER_ID, MapLayer.ID_OSM);
        if (!MapLayer.ID_OSM.equals(id) && !MapLayer.ID_CUSTOM.equals(id)) {
            // Built-in "gaode"/"esri" layers were removed; migrate to OSM.
            id = MapLayer.ID_OSM;
            prefs.edit().putString(Prefs.LAYER_ID, MapLayer.ID_OSM).apply();
        }
        String url = prefs.getString(Prefs.CUSTOM_URL, null);
        MapLayer.CoordSystem coord;
        try {
            coord = MapLayer.CoordSystem.valueOf(prefs.getString(
                    Prefs.CUSTOM_COORD, MapLayer.CoordSystem.WGS84.name()));
        } catch (IllegalArgumentException e) {
            coord = MapLayer.CoordSystem.WGS84;
        }

        boolean same = id.equals(currentLayer.id)
                && java.util.Objects.equals(url, customTileUrl)
                && coord == customTileCoord;
        if (same) {
            return;
        }
        if (lastSnapshot.isActive()) {
            toast(R.string.toast_layer_locked);
            return;
        }

        MapLayer desired;
        if (MapLayer.ID_CUSTOM.equals(id)) {
            if (!TileSources.isValidTemplate(url)) {
                return;
            }
            try {
                desired = TileSources.custom(url, coord);
            } catch (IllegalArgumentException e) {
                return;
            }
        } else {
            desired = TileSources.builtIn(id);
        }
        customTileUrl = url;
        customTileCoord = coord;
        applyLayer(desired, true);
    }

    private void undoStroke() {
        if (lastSnapshot.isActive() || routeStrokes.isEmpty()) {
            return;
        }
        routeStrokes.remove(routeStrokes.size() - 1);
        rebuildRoute();
    }

    private void confirmClearRoute() {
        if (lastSnapshot.isActive() || routePoints.isEmpty()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_clear_title)
                .setMessage(R.string.dialog_clear_message)
                .setPositiveButton(R.string.dialog_clear_ok,
                        (dialog, which) -> {
                            routeStrokes.clear();
                            rebuildRoute();
                        })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    // ---------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------

    private void setDrawMode(boolean enabled) {
        if (enabled && lastSnapshot.isActive()) {
            toast(R.string.toast_layer_locked);
            return;
        }
        drawMode = enabled;
        drawOverlay.setDrawingEnabled(enabled);
        drawOverlay.setVisibility(enabled ? View.VISIBLE : View.GONE);
        btnDraw.setText(enabled ? R.string.btn_finish_draw : R.string.btn_draw);
        if (enabled) {
            mapView.getController().stopAnimation(true);
            toast(R.string.toast_draw_hint);
        } else {
            drawOverlay.cancelStroke();
        }
    }

    // ---------------------------------------------------------------------
    // Speed / follow
    // ---------------------------------------------------------------------

    private void setSpeedKmh(double kmh) {
        settingSpeed = true;
        editSpeed.setText(String.format(Locale.US, "%.1f", kmh));
        editSpeed.setSelection(editSpeed.getText() == null ? 0 : editSpeed.getText().length());
        settingSpeed = false;
        speedLayout.setError(null);
        prefs.edit().putFloat(Prefs.SPEED_KMH, (float) kmh).apply();
        updateRouteInfo();
        updateButtons();
        applyLiveSpeed(kmh);
    }

    private void onSpeedTextChanged() {
        if (settingSpeed) {
            return;
        }
        String text = editSpeed.getText() == null ? "" : editSpeed.getText().toString().trim();
        double kmh = parseSpeedKmh();
        if (text.isEmpty()) {
            speedLayout.setError(null);
        } else if (kmh <= 0) {
            speedLayout.setError(getString(R.string.toast_speed_invalid));
        } else {
            speedLayout.setError(null);
            prefs.edit().putFloat(Prefs.SPEED_KMH, (float) kmh).apply();
            applyLiveSpeed(kmh);
        }
        updateRouteInfo();
        updateButtons();
    }

    private void applyLiveSpeed(double kmh) {
        if (kmh > 0 && service != null && lastSnapshot.isActive()) {
            service.setSpeedMps(kmh / 3.6);
        }
    }

    private double parseSpeedKmh() {
        if (editSpeed == null || editSpeed.getText() == null) {
            return -1.0;
        }
        String text = editSpeed.getText().toString().trim();
        if (text.isEmpty()) {
            return -1.0;
        }
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value) || value < 0.1 || value > 300.0) {
                return -1.0;
            }
            return value;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    private void updateMockStatus() {
        if (textMockStatus == null) {
            return;
        }
        if (lastSnapshot.isActive() && service != null) {
            ServiceDiagnostics diagnostics = service.getDiagnostics();
            if (diagnostics != null) {
                String providers = getString(R.string.mock_status_providers,
                        diagnostics.gpsReady ? "✓" : "✗",
                        diagnostics.networkReady ? "✓" : "✗",
                        diagnostics.fusedReady ? "✓" : "✗");
                textMockStatus.setText(getString(R.string.mock_status_running,
                        providers, diagnostics.injectedCount, diagnostics.echoCount));
                textMockStatus.setTextColor(ContextCompat.getColor(this,
                        diagnostics.echoCount > 0 ? R.color.status_ok : R.color.status_error));
                return;
            }
        }

        if (!MockLocationHelper.isDeveloperOptionsEnabled(this)) {
            setMockStatus(R.string.mock_status_no_dev, false);
            return;
        }
        if (!DeviceState.hasFineLocationPermission(this)) {
            setMockStatus(R.string.mock_status_no_permission, false);
            return;
        }
        if (!DeviceState.isLocationServiceEnabled(this)) {
            setMockStatus(R.string.mock_status_no_location, false);
            return;
        }
        if (!MockLocationHelper.isMockLocationSelected(this)) {
            setMockStatus(R.string.mock_status_not_selected, false);
            return;
        }
        setMockStatus(R.string.mock_status_ready, true);
    }

    private void setMockStatus(int stringRes, boolean ok) {
        textMockStatus.setText(stringRes);
        textMockStatus.setTextColor(ContextCompat.getColor(this,
                ok ? R.color.status_ok : R.color.status_error));
    }

    private void handleMockStatusClick() {
        openSettings();
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void toggleFollow() {
        follow = !follow;
        prefs.edit().putBoolean(Prefs.FOLLOW, follow).apply();
        updateFollowUi();
        toast(follow ? R.string.toast_follow_on : R.string.toast_follow_off);
        if (follow && lastSnapshot.isActive() && lastSnapshot.hasPosition()) {
            mapView.getController().setCenter(
                    toMapGeoPoint(new PointD(lastSnapshot.lat, lastSnapshot.lng)));
        }
    }

    private void updateFollowUi() {
        fabFollow.setAlpha(follow ? 1.0f : 0.55f);
    }

    private void locateMe() {
        if (lastSnapshot.isActive() && lastSnapshot.hasPosition()) {
            mapView.getController().animateTo(
                    toMapGeoPoint(new PointD(lastSnapshot.lat, lastSnapshot.lng)));
            return;
        }
        if (!DeviceState.hasFineLocationPermission(this)) {
            requestPermissions();
            return;
        }
        Location location = getLastKnownLocation();
        if (location == null) {
            toast(R.string.toast_location_unavailable);
            return;
        }
        mapView.getController().animateTo(
                toMapGeoPoint(new PointD(location.getLatitude(), location.getLongitude())));
        mapView.getController().setZoom(17.0);
    }

    // ---------------------------------------------------------------------
    // Service control
    // ---------------------------------------------------------------------

    private void startSimulation() {
        if (!lastSnapshot.canStart() || starting) {
            return;
        }
        if (drawMode) {
            setDrawMode(false);
        }
        if (!joystickMode
                && (routePoints.size() < 2
                || routeDistanceMeters < MIN_ROUTE_METERS)) {
            toast(routePoints.isEmpty()
                    ? R.string.toast_need_route : R.string.toast_route_too_short);
            return;
        }
        double kmh = parseSpeedKmh();
        if (kmh <= 0) {
            speedLayout.setError(getString(R.string.toast_speed_invalid));
            toast(R.string.toast_speed_invalid);
            return;
        }
        if (joystickMode && !Settings.canDrawOverlays(this)) {
            // Without the overlay there is no way to steer a joystick run;
            // refuse to start silently frozen at the map center.
            showOverlayPermissionDialog();
            return;
        }
        if (!ensureStartPrerequisites()) {
            return;
        }

        Intent intent = new Intent(this, MockLocationService.class);
        intent.setAction(MockLocationService.ACTION_START);
        intent.putExtra(MockLocationService.EXTRA_SPEED_MPS, kmh / 3.6);

        if (joystickMode) {
            IGeoPoint center = mapView.getMapCenter();
            PointD start = joystickStartWgs != null ? joystickStartWgs
                    : toWgsPoint(new PointD(center.getLatitude(), center.getLongitude()));
            intent.putExtra(MockLocationService.EXTRA_MODE,
                    SimulationSnapshot.MODE_JOYSTICK);
            intent.putExtra(MockLocationService.EXTRA_START_LAT, start.lat);
            intent.putExtra(MockLocationService.EXTRA_START_LNG, start.lng);
            intent.putExtra(MockLocationService.EXTRA_LOOP, false);
        } else {
            List<PointD> wgsRoute = new ArrayList<>(routePoints.size());
            for (GeoPoint point : routePoints) {
                wgsRoute.add(toWgsPoint(
                        new PointD(point.getLatitude(), point.getLongitude())));
            }
            double[] lats = new double[wgsRoute.size()];
            double[] lngs = new double[wgsRoute.size()];
            for (int i = 0; i < wgsRoute.size(); i++) {
                lats[i] = wgsRoute.get(i).lat;
                lngs[i] = wgsRoute.get(i).lng;
            }
            intent.putExtra(MockLocationService.EXTRA_MODE,
                    SimulationSnapshot.MODE_ROUTE);
            intent.putExtra(MockLocationService.EXTRA_LATS, lats);
            intent.putExtra(MockLocationService.EXTRA_LNGS, lngs);
            intent.putExtra(MockLocationService.EXTRA_LOOP, checkLoop.isChecked());
        }

        lastErrorShown = null;
        starting = true;
        updateRouteOverlays();
        updateButtons();
        try {
            ContextCompat.startForegroundService(this, intent);
        } catch (Exception e) {
            starting = false;
            updateRouteOverlays();
            updateButtons();
            showErrorDialog(getString(R.string.error_foreground_start));
            return;
        }

        uiHandler.postDelayed(() -> {
            if (starting) {
                starting = false;
                updateRouteOverlays();
                updateButtons();
            }
        }, 3000L);
    }

    private boolean ensureStartPrerequisites() {
        if (!DeviceState.hasFineLocationPermission(this)) {
            toast(R.string.toast_need_location);
            requestPermissions();
            return false;
        }
        if (!DeviceState.isLocationServiceEnabled(this)) {
            showLocationServiceDialog();
            return false;
        }
        if (DeviceState.isWifiEnabled(this)) {
            toast(R.string.toast_wifi_hint);
        }
        if (!MockLocationHelper.isDeveloperOptionsEnabled(this)) {
            showDeveloperOptionsDialog();
            return false;
        }
        if (!MockLocationHelper.isMockLocationSelected(this)) {
            showMockLocationDialog();
            return false;
        }
        return true;
    }

    private void togglePause() {
        if (service == null) {
            return;
        }
        if (lastSnapshot.state == SimulationSnapshot.STATE_RUNNING) {
            service.pause();
        } else if (lastSnapshot.state == SimulationSnapshot.STATE_PAUSED) {
            service.resume();
        }
    }

    private void stopSimulation() {
        if (service != null) {
            service.stopSimulation();
        } else {
            Intent intent = new Intent(this, MockLocationService.class);
            intent.setAction(MockLocationService.ACTION_STOP);
            try {
                startService(intent);
            } catch (Exception ignored) {
            }
        }
        toast(R.string.toast_stopped);
    }

    private void applySnapshot(SimulationSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        int previousState = lastSnapshot.state;
        boolean stateChanged = snapshot.state != previousState;

        if (snapshot.state == SimulationSnapshot.STATE_ERROR) {
            lastSnapshot = snapshot;
            starting = false;
            currentMarker.setVisible(false);
            updateRouteOverlays();
            updateButtons();
            updateRouteInfo();
            updateMockStatus();
            showErrorDialog(snapshot.error);
            return;
        }

        lastSnapshot = snapshot;
        starting = false;

        // Keep the activity's speed field in sync when the floating window
        // (or another surface) changes the speed while this activity is alive.
        if (snapshot.isActive() && snapshot.speedMps > 0.01 && !editSpeed.hasFocus()) {
            double kmh = snapshot.speedMps * 3.6;
            double current = parseSpeedKmh();
            if (current <= 0 || Math.abs(current - kmh) > 0.11) {
                setSpeedKmh(kmh);
            }
        }

        if (snapshot.isActive() && snapshot.hasPosition()) {
            GeoPoint position = toMapGeoPoint(new PointD(snapshot.lat, snapshot.lng));
            currentMarker.setPosition(position);
            currentMarker.setRotation((float) snapshot.bearingDegrees);
            currentMarker.setVisible(true);
            if (follow && snapshot.state == SimulationSnapshot.STATE_RUNNING) {
                mapView.getController().setCenter(position);
            }
        } else {
            currentMarker.setVisible(false);
        }

        if (stateChanged) {
            lastTextUpdateElapsed = 0L;
            updateRouteOverlays();
            updateButtons();
        }

        long now = SystemClock.elapsedRealtime();
        if (stateChanged || now - lastTextUpdateElapsed >= 500L) {
            lastTextUpdateElapsed = now;
            updateRouteInfo();
        }
        if (stateChanged || now - lastMockStatusElapsed >= 1000L) {
            lastMockStatusElapsed = now;
            updateMockStatus();
        }
        mapView.invalidate();
    }

    private void showErrorDialog(String message) {
        if (message == null || message.equals(lastErrorShown)) {
            return;
        }
        lastErrorShown = message;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show();
    }

    // ---------------------------------------------------------------------
    // Layers
    // ---------------------------------------------------------------------

    private void applyLayer(MapLayer layer, boolean convertRoute) {
        if (lastSnapshot.isActive()) {
            toast(R.string.toast_layer_locked);
            return;
        }
        if (drawMode) {
            setDrawMode(false);
        }

        MapLayer.CoordSystem oldSystem =
                currentLayer == null ? layer.coordSystem : currentLayer.coordSystem;
        boolean convert = convertRoute && currentLayer != null
                && oldSystem != layer.coordSystem;

        if (convert) {
            for (List<GeoPoint> stroke : routeStrokes) {
                for (int i = 0; i < stroke.size(); i++) {
                    GeoPoint point = stroke.get(i);
                    PointD converted = convertCoord(
                            new PointD(point.getLatitude(), point.getLongitude()),
                            oldSystem, layer.coordSystem);
                    stroke.set(i, new GeoPoint(converted.lat, converted.lng));
                }
            }

            IGeoPoint center = mapView.getMapCenter();
            PointD convertedCenter = convertCoord(
                    new PointD(center.getLatitude(), center.getLongitude()),
                    oldSystem, layer.coordSystem);
            mapView.getController().setCenter(
                    new GeoPoint(convertedCenter.lat, convertedCenter.lng));
        }

        currentLayer = layer;
        mapView.setTileSource(layer.tileSource);
        textAttribution.setText(layer.attribution);
        saveLayerPrefs();
        rebuildRoute();
        mapView.invalidate();
    }

    private PointD convertCoord(PointD point, MapLayer.CoordSystem from,
                                MapLayer.CoordSystem to) {
        if (from == to) {
            return point;
        }
        if (from == MapLayer.CoordSystem.GCJ02 && to == MapLayer.CoordSystem.WGS84) {
            return CoordConverter.gcj02ToWgs84(point);
        }
        if (from == MapLayer.CoordSystem.WGS84 && to == MapLayer.CoordSystem.GCJ02) {
            return CoordConverter.wgs84ToGcj02(point);
        }
        return point;
    }

    // ---------------------------------------------------------------------
    // Dialogs / permissions
    // ---------------------------------------------------------------------

    private void showOverlayPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_overlay_title)
                .setMessage(R.string.dialog_overlay_message)
                .setPositiveButton(R.string.dialog_overlay_settings, (dialog, which) -> {
                    try {
                        startActivity(new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        toast(R.string.settings_overlay_denied);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showMockLocationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_mock_title)
                .setMessage(R.string.dialog_mock_message)
                .setPositiveButton(R.string.dialog_open_settings,
                        (dialog, which) -> openDeveloperSettings())
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showDeveloperOptionsDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_developer_title)
                .setMessage(R.string.dialog_developer_message)
                .setPositiveButton(R.string.dialog_open_settings,
                        (dialog, which) -> openDeveloperSettings())
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void openDeveloperSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private void showHelpDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_help_title)
                .setMessage(R.string.dialog_help_message)
                .setPositiveButton(R.string.dialog_ok, null)
                .setNeutralButton(R.string.settings_title,
                        (dialog, which) -> openSettings())
                .show();
    }

    private void showLocationServiceDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("需要开启系统定位")
                .setMessage("请开启系统定位服务（GPS）。关闭定位时，测试 provider 可能不会向其他应用投递位置。")
                .setPositiveButton("打开定位设置", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showDisclaimerIfNeeded() {
        if (prefs.getBoolean(Prefs.DISCLAIMER_ACCEPTED, false)) {
            requestPermissionsIfNeeded();
            return;
        }
        View content = getLayoutInflater().inflate(R.layout.dialog_disclaimer, null);
        MaterialCheckBox checkBox = content.findViewById(R.id.disclaimerCheck);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_disclaimer_title)
                .setView(content)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_agree, null)
                .setNegativeButton(R.string.dialog_exit, (d, which) -> finish())
                .create();
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false);
            checkBox.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> positive.setEnabled(isChecked));
            positive.setOnClickListener(v -> {
                prefs.edit().putBoolean(Prefs.DISCLAIMER_ACCEPTED, true).apply();
                dialog.dismiss();
                requestPermissionsIfNeeded();
            });
        });
        dialog.show();
    }

    private void requestPermissionsIfNeeded() {
        List<String> permissions = new ArrayList<>();
        if (!DeviceState.hasFineLocationPermission(this)) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        ActivityCompat.requestPermissions(this,
                permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) {
            return;
        }
        if (!DeviceState.hasFineLocationPermission(this)) {
            showPermissionDialog();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            toast(R.string.toast_notifications_denied);
        }
        updateButtons();
        updateMockStatus();
    }

    private void showPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_permission_title)
                .setMessage(R.string.dialog_permission_message)
                .setPositiveButton(R.string.dialog_open_app_settings, (dialog, which) -> {
                    try {
                        startActivity(new Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    @SuppressLint("MissingPermission")
    @Nullable
    private Location getLastKnownLocation() {
        if (!DeviceState.hasFineLocationPermission(this)) {
            return null;
        }
        LocationManager manager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return null;
        }
        try {
            Location gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            return gps != null ? gps : network;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Lifecycle / binding / persistence
    // ---------------------------------------------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        bindingRequested = bindService(new Intent(this, MockLocationService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        syncServiceAndSettings();
        updateMockStatus();
    }

    private void syncServiceAndSettings() {
        // Binding is asynchronous. Wait for the current service state before
        // deciding whether settings changed while this activity was stopped
        // are locked (e.g. after Stop was pressed in the notification).
        if (service == null) {
            return;
        }
        applySnapshot(service.getSnapshot());
        syncLayerFromPrefs();
        syncModeFromPrefs();
    }

    @Override
    protected void onPause() {
        saveMapState();
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (bindingRequested) {
            if (service != null) {
                service.removeListener(snapshotListener);
            }
            try {
                unbindService(serviceConnection);
            } catch (IllegalArgumentException ignored) {
                // The connection had not completed yet.
            }
            bindingRequested = false;
            bound = false;
            service = null;
        }
        saveMapState();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        if (mapView != null) {
            mapView.onDetach();
        }
        super.onDestroy();
    }

    private void saveRoute() {
        if (currentLayer == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int strokeIndex = 0; strokeIndex < routeStrokes.size(); strokeIndex++) {
            if (strokeIndex > 0) {
                builder.append('|');
            }
            List<GeoPoint> stroke = routeStrokes.get(strokeIndex);
            for (int pointIndex = 0; pointIndex < stroke.size(); pointIndex++) {
                if (pointIndex > 0) {
                    builder.append(';');
                }
                GeoPoint point = stroke.get(pointIndex);
                builder.append(String.format(Locale.US, "%.7f,%.7f",
                        point.getLatitude(), point.getLongitude()));
            }
        }
        prefs.edit()
                .putString(Prefs.ROUTE, builder.toString())
                .putString(Prefs.ROUTE_COORD, currentLayer.coordSystem.name())
                .apply();
    }

    private void saveLayerPrefs() {
        if (currentLayer == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putString(Prefs.LAYER_ID, currentLayer.id)
                .putString(Prefs.CUSTOM_COORD, customTileCoord.name());
        if (customTileUrl != null) {
            editor.putString(Prefs.CUSTOM_URL, customTileUrl);
        }
        editor.apply();
    }

    private void saveMapState() {
        if (mapView == null || currentLayer == null) {
            return;
        }
        IGeoPoint center = mapView.getMapCenter();
        if (center == null) {
            return;
        }
        PointD wgs = toWgsPoint(new PointD(center.getLatitude(), center.getLongitude()));
        prefs.edit()
                .putString(Prefs.CENTER_LAT_WGS, Double.toString(wgs.lat))
                .putString(Prefs.CENTER_LNG_WGS, Double.toString(wgs.lng))
                .putFloat(Prefs.ZOOM, (float) mapView.getZoomLevelDouble())
                .apply();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private GeoPoint toMapGeoPoint(PointD wgs) {
        PointD map = currentLayer != null && currentLayer.usesGcj02()
                ? CoordConverter.wgs84ToGcj02(wgs) : wgs;
        return new GeoPoint(map.lat, map.lng);
    }

    private PointD toWgsPoint(PointD mapPoint) {
        return currentLayer != null && currentLayer.usesGcj02()
                ? CoordConverter.gcj02ToWgs84(mapPoint) : mapPoint;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(editSpeed.getWindowToken(), 0);
        }
    }
}
