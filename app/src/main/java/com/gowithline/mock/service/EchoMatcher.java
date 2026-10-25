package com.gowithline.mock.service;

import com.gowithline.mock.geo.GeoMath;

/**
 * Pure-Java decision logic for the location self-check echo detection.
 *
 * <p>A callback is only a genuine echo of our own test-provider injection when
 * the platform marks it as mock-sourced and its coordinates are within the
 * echo radius of the most recently injected point. A real location that
 * happens to be near the injected point must not be counted.
 */
public final class EchoMatcher {
    /** Maximum distance between injection and callback that still counts. */
    static final double MAX_ECHO_DISTANCE_METERS = 50.0;

    private EchoMatcher() {
    }

    public static boolean isEcho(double injectedLat, double injectedLng,
                                 double callbackLat, double callbackLng,
                                 boolean callbackFromMockProvider) {
        if (!callbackFromMockProvider) {
            return false;
        }
        if (Double.isNaN(injectedLat) || Double.isNaN(injectedLng)) {
            return false;
        }
        double distance = GeoMath.distanceMeters(
                injectedLat, injectedLng, callbackLat, callbackLng);
        return distance <= MAX_ECHO_DISTANCE_METERS;
    }
}
