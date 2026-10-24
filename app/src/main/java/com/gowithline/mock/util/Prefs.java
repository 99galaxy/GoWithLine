package com.gowithline.mock.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single source of truth for the app's preference file and keys.
 */
public final class Prefs {
    public static final String FILE = "gowithline";

    public static final String DISCLAIMER_ACCEPTED = "disclaimer_accepted";
    public static final String LAYER_ID = "layer_id";
    public static final String CUSTOM_URL = "custom_url";
    public static final String CUSTOM_COORD = "custom_coord";
    public static final String ROUTE = "route";
    public static final String ROUTE_COORD = "route_coord";
    public static final String SPEED_KMH = "speed_kmh";
    public static final String LOOP = "loop";
    public static final String FOLLOW = "follow";
    public static final String FLOATING_ENABLED = "floating_enabled";
    public static final String FLOATING_X = "floating_x";
    public static final String FLOATING_Y = "floating_y";
    public static final String CENTER_LAT_WGS = "center_lat_wgs";
    public static final String CENTER_LNG_WGS = "center_lng_wgs";
    public static final String ZOOM = "zoom";

    public static final String SIMULATION_MODE = "simulation_mode";
    public static final String MODE_ROUTE = "route";
    public static final String MODE_JOYSTICK = "joystick";

    private Prefs() {
    }

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
