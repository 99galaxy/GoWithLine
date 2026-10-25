package com.gowithline.mock.service;

import com.gowithline.mock.geo.GeoMath;
import com.gowithline.mock.geo.PointD;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EchoMatcherTest {
    private static final PointD INJECTED = new PointD(39.9087, 116.3975);

    private static PointD pointAtMeters(double meters) {
        return GeoMath.destinationPoint(INJECTED, 45.0, meters);
    }

    @Test
    public void realNearbyCallbackIsNotCountedAsEcho() {
        PointD realNearby = pointAtMeters(10.0);

        assertFalse(EchoMatcher.isEcho(
                INJECTED.lat, INJECTED.lng,
                realNearby.lat, realNearby.lng,
                false));
    }

    @Test
    public void mockNearbyCallbackIsCountedAsEcho() {
        PointD mockNearby = pointAtMeters(10.0);

        assertTrue(EchoMatcher.isEcho(
                INJECTED.lat, INJECTED.lng,
                mockNearby.lat, mockNearby.lng,
                true));
    }

    @Test
    public void mockFarCallbackIsNotCountedAsEcho() {
        PointD mockFar = pointAtMeters(100.0);

        assertFalse(EchoMatcher.isEcho(
                INJECTED.lat, INJECTED.lng,
                mockFar.lat, mockFar.lng,
                true));
    }
}
