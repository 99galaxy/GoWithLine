package com.gowithline.mock.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

/**
 * Device-level checks shared by the main screen and settings.
 */
public final class DeviceState {
    private DeviceState() {
    }

    public static boolean hasFineLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isLocationServiceEnabled(Context context) {
        LocationManager manager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return manager.isLocationEnabled();
            }
            return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isWifiEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        return wifiManager != null && wifiManager.isWifiEnabled();
    }
}
