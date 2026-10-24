package com.gowithline.mock.util;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.content.Context;
import android.location.Criteria;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

/**
 * Helpers for the Android "select mock location app" developer option.
 *
 * <p>The pre-check deliberately adds a throwaway provider instead of GPS so a
 * failed check never disturbs the real provider. If a vendor rejects unknown
 * provider names we fall back to a GPS probe, the approach used by GoGoGo.</p>
 */
public final class MockLocationHelper {
    private static final String PROBE_PROVIDER = "gowithline_mock_probe";

    private MockLocationHelper() {
    }

    public static boolean isDeveloperOptionsEnabled(Context context) {
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0) == 1;
        } catch (Exception ignored) {
            // Some OEMs restrict this global setting. Let the provider probe
            // decide instead of blocking the user.
            return true;
        }
    }

    /**
     * Returns true when this package is selected under
     * Developer options -> Select mock location app.
     */
    public static boolean isMockLocationSelected(Context context) {
        if (isMockAppOpAllowed(context)) {
            return true;
        }

        LocationManager manager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return false;
        }

        removeTestProvider(manager, PROBE_PROVIDER);
        try {
            addTestProvider(manager, PROBE_PROVIDER,
                    false, true, false, false, true, true, true, true);
            manager.setTestProviderEnabled(PROBE_PROVIDER, true);
            return true;
        } catch (SecurityException e) {
            return false;
        } catch (IllegalArgumentException e) {
            return probeWithGps(manager);
        } catch (Exception e) {
            return false;
        } finally {
            removeTestProvider(manager, PROBE_PROVIDER);
        }
    }

    public static boolean isMockAppOpAllowed(Context context) {
        try {
            AppOpsManager appOps =
                    (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return false;
            }
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = appOps.unsafeCheckOpNoThrow("android:mock_location",
                        Process.myUid(), context.getPackageName());
            } else {
                mode = appOps.checkOpNoThrow("android:mock_location",
                        Process.myUid(), context.getPackageName());
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean probeWithGps(LocationManager manager) {
        removeTestProvider(manager, LocationManager.GPS_PROVIDER);
        try {
            addTestProvider(manager, LocationManager.GPS_PROVIDER,
                    false, true, false, false, true, true, true, true);
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            removeTestProvider(manager, LocationManager.GPS_PROVIDER);
        }
    }

    /**
     * Adds a test provider across API levels. On API 31+ the numeric
     * ProviderProperties constants are compile-time inlined, so this method
     * still loads on Android 8-11.
     */
    @SuppressLint("WrongConstant")
    public static void addTestProvider(LocationManager manager, String name,
                                       boolean requiresNetwork, boolean requiresSatellite,
                                       boolean requiresCell, boolean hasMonetaryCost,
                                       boolean supportsAltitude, boolean supportsSpeed,
                                       boolean supportsBearing, boolean fine) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.addTestProvider(name, requiresNetwork, requiresSatellite, requiresCell,
                    hasMonetaryCost, supportsAltitude, supportsSpeed, supportsBearing,
                    fine ? ProviderProperties.POWER_USAGE_HIGH : ProviderProperties.POWER_USAGE_LOW,
                    fine ? ProviderProperties.ACCURACY_FINE : ProviderProperties.ACCURACY_COARSE);
        } else {
            manager.addTestProvider(name, requiresNetwork, requiresSatellite, requiresCell,
                    hasMonetaryCost, supportsAltitude, supportsSpeed, supportsBearing,
                    fine ? Criteria.POWER_HIGH : Criteria.POWER_LOW,
                    fine ? Criteria.ACCURACY_FINE : Criteria.ACCURACY_COARSE);
        }
    }

    public static void removeTestProvider(LocationManager manager, String name) {
        try {
            manager.setTestProviderEnabled(name, false);
        } catch (Exception ignored) {
        }
        try {
            manager.removeTestProvider(name);
        } catch (Exception ignored) {
        }
    }
}
