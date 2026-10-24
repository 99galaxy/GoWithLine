package com.gowithline.mock.geo;

/**
 * Immutable latitude/longitude pair used by the Android-independent geo layer.
 */
public final class PointD {
    public final double lat;
    public final double lng;

    public PointD(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    @Override
    public String toString() {
        return lat + "," + lng;
    }
}
