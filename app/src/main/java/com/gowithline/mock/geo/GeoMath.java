package com.gowithline.mock.geo;

/**
 * Spherical earth helpers. All angles are in degrees at the API boundary.
 */
public final class GeoMath {
    public static final double EARTH_RADIUS_METERS = 6371008.8;

    private GeoMath() {
    }

    public static double distanceMeters(PointD a, PointD b) {
        return distanceMeters(a.lat, a.lng, b.lat, b.lng);
    }

    /** Great-circle distance in meters. */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lng2 - lng1);

        double sinPhi = Math.sin(dPhi / 2.0);
        double sinLambda = Math.sin(dLambda / 2.0);
        double h = sinPhi * sinPhi
                + Math.cos(phi1) * Math.cos(phi2) * sinLambda * sinLambda;
        h = Math.min(1.0, Math.max(0.0, h));
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
    }

    /** Initial bearing from a to b, clockwise from north in [0, 360). */
    public static double bearingDegrees(PointD a, PointD b) {
        return bearingDegrees(a.lat, a.lng, b.lat, b.lng);
    }

    public static double bearingDegrees(double lat1, double lng1, double lat2, double lng2) {
        if (lat1 == lat2 && lng1 == lng2) {
            return 0.0;
        }
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLambda = Math.toRadians(lng2 - lng1);

        double y = Math.sin(dLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);
        double degrees = Math.toDegrees(Math.atan2(y, x));
        return normalizeBearing(degrees);
    }

    public static double normalizeBearing(double degrees) {
        double result = degrees % 360.0;
        return result < 0 ? result + 360.0 : result;
    }

    /** Meters per degree of longitude at the given latitude. */
    public static double metersPerDegreeLng(double latitude) {
        return Math.toRadians(1.0) * EARTH_RADIUS_METERS
                * Math.cos(Math.toRadians(latitude));
    }

    /** Meters per degree of latitude. */
    public static double metersPerDegreeLat() {
        return Math.toRadians(1.0) * EARTH_RADIUS_METERS;
    }

    /**
     * Moves from {@code start} along a bearing by a distance on the sphere.
     * Used by the joystick mode, where the bearing changes frequently but each
     * step is only a few meters.
     */
    public static PointD destinationPoint(PointD start, double bearingDegrees,
                                          double distanceMeters) {
        if (distanceMeters == 0.0) {
            return new PointD(start.lat, start.lng);
        }
        double angularDistance = distanceMeters / EARTH_RADIUS_METERS;
        double bearing = Math.toRadians(bearingDegrees);
        double lat1 = Math.toRadians(start.lat);
        double lng1 = Math.toRadians(start.lng);

        double sinLat2 = Math.sin(lat1) * Math.cos(angularDistance)
                + Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearing);
        double lat2 = Math.asin(Math.max(-1.0, Math.min(1.0, sinLat2)));
        double lng2 = lng1 + Math.atan2(
                Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(lat1),
                Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2));

        double lngDegrees = (Math.toDegrees(lng2) + 540.0) % 360.0 - 180.0;
        return new PointD(Math.toDegrees(lat2), lngDegrees);
    }
}
