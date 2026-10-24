package com.gowithline.mock.geo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeoMathTest {
    @Test
    public void oneDegreeOfLongitudeAtEquator() {
        assertEquals(111195.0, GeoMath.distanceMeters(0, 0, 0, 1), 30.0);
    }

    @Test
    public void oneDegreeOfLatitude() {
        assertEquals(111195.0, GeoMath.distanceMeters(0, 0, 1, 0), 30.0);
    }

    @Test
    public void cardinalBearings() {
        assertEquals(0.0, GeoMath.bearingDegrees(0, 0, 1, 0), 1e-6);
        assertEquals(90.0, GeoMath.bearingDegrees(0, 0, 0, 1), 1e-6);
        assertEquals(180.0, GeoMath.bearingDegrees(1, 0, 0, 0), 1e-6);
        assertEquals(270.0, GeoMath.bearingDegrees(0, 1, 0, 0), 1e-6);
    }

    @Test
    public void zeroDistanceHasZeroBearing() {
        assertEquals(0.0, GeoMath.distanceMeters(39.9, 116.4, 39.9, 116.4), 1e-9);
        assertEquals(0.0, GeoMath.bearingDegrees(39.9, 116.4, 39.9, 116.4), 1e-9);
    }

    @Test
    public void bearingIsNormalized() {
        assertEquals(350.0, GeoMath.normalizeBearing(-10.0), 1e-9);
        assertEquals(10.0, GeoMath.normalizeBearing(370.0), 1e-9);
    }

    @Test
    public void destinationPointNorthAndEast() {
        PointD start = new PointD(0, 0);

        PointD north = GeoMath.destinationPoint(start, 0, 111195);
        assertEquals(1.0, north.lat, 1e-3);
        assertEquals(0.0, north.lng, 1e-3);

        PointD east = GeoMath.destinationPoint(start, 90, 111195);
        assertEquals(0.0, east.lat, 1e-3);
        assertEquals(1.0, east.lng, 1e-3);
    }

    @Test
    public void destinationPointDistanceRoundTrip() {
        PointD start = new PointD(39.9087, 116.3975);
        PointD end = GeoMath.destinationPoint(start, 45, 100);
        assertEquals(100.0, GeoMath.distanceMeters(start, end), 0.5);
    }
}
