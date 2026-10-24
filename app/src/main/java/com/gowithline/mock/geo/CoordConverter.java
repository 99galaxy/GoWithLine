package com.gowithline.mock.geo;

/**
 * WGS-84 <-> GCJ-02 conversion.
 *
 * <p>Some Chinese map providers publish tiles in an obfuscated datum. Android's
 * location API, however, speaks WGS-84. The map layer keeps
 * every coordinate in the tile datum; only the route handed to the mock
 * location provider is converted back to WGS-84. This mirrors what GoGoGo does
 * with BD-09 map coordinates.</p>
 */
public final class CoordConverter {
    private static final double PI = 3.14159265358979323846;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private CoordConverter() {
    }

    public static boolean isOutOfChina(double lng, double lat) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    public static PointD wgs84ToGcj02(PointD wgs) {
        return wgs84ToGcj02(wgs.lng, wgs.lat);
    }

    public static PointD wgs84ToGcj02(double lng, double lat) {
        if (isOutOfChina(lng, lat)) {
            return new PointD(lat, lng);
        }

        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLon(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);

        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);

        return new PointD(lat + dLat, lng + dLng);
    }

    public static PointD gcj02ToWgs84(PointD gcj) {
        return gcj02ToWgs84(gcj.lng, gcj.lat);
    }

    /**
     * Iterative inverse of {@link #wgs84ToGcj02(double, double)}. The forward
     * transform has no closed-form inverse; a few fixed-point iterations reach
     * sub-centimetre accuracy.
     */
    public static PointD gcj02ToWgs84(double lng, double lat) {
        if (isOutOfChina(lng, lat)) {
            return new PointD(lat, lng);
        }

        double wgsLng = lng;
        double wgsLat = lat;
        for (int i = 0; i < 8; i++) {
            PointD transformed = wgs84ToGcj02(wgsLng, wgsLat);
            double dLng = lng - transformed.lng;
            double dLat = lat - transformed.lat;
            wgsLng += dLng;
            wgsLat += dLat;
            if (Math.abs(dLng) < 1e-10 && Math.abs(dLat) < 1e-10) {
                break;
            }
        }
        return new PointD(wgsLat, wgsLng);
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
                + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y
                + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
}
