package com.gowithline.mock.geo;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RoutePathTest {
    @Test
    public void interpolatesHalfwayAlongAStraightLine() {
        RoutePath path = new RoutePath(Arrays.asList(
                new PointD(0, 0),
                new PointD(0, 0.001)));

        assertEquals(111.195, path.getTotalMeters(), 0.05);

        RoutePath.Sample start = path.sampleAt(0);
        assertEquals(0.0, start.point.lng, 1e-12);
        assertEquals(90.0, start.bearingDegrees, 1e-6);

        RoutePath.Sample middle = path.sampleAt(path.getTotalMeters() / 2.0);
        assertEquals(0.0005, middle.point.lng, 1e-7);
        assertEquals(90.0, middle.bearingDegrees, 1e-6);

        RoutePath.Sample end = path.sampleAt(path.getTotalMeters());
        assertEquals(0.001, end.point.lng, 1e-12);
    }

    @Test
    public void followsTheSecondSegmentBearing() {
        RoutePath path = new RoutePath(Arrays.asList(
                new PointD(0, 0),
                new PointD(0, 0.001),
                new PointD(0.001, 0.001)));

        double secondSegmentDistance = path.getTotalMeters() * 0.75;
        RoutePath.Sample sample = path.sampleAt(secondSegmentDistance);
        assertEquals(0.0, sample.bearingDegrees, 1e-6);
        assertTrue(sample.segmentIndex >= 1);
    }

    @Test
    public void clampsOutOfRangeDistances() {
        RoutePath path = new RoutePath(Arrays.asList(
                new PointD(10, 20),
                new PointD(10, 20.001)));
        assertEquals(20.0, path.sampleAt(-100).point.lng, 1e-12);
        assertEquals(20.001, path.sampleAt(1_000_000).point.lng, 1e-12);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyRoutes() {
        new RoutePath(List.of());
    }

    @Test
    public void crossesDateLineEastwardWithoutJumpingToGreenwich() {
        RoutePath path = new RoutePath(Arrays.asList(
                new PointD(0, 179.999), new PointD(0, -179.999)));

        assertEquals(222.390, path.getTotalMeters(), 0.01);
        assertEquals(179.9995, path.sampleAt(path.getTotalMeters() * 0.25).point.lng, 1e-7);
        assertEquals(180.0, Math.abs(path.sampleAt(path.getTotalMeters() * 0.5).point.lng), 1e-7);
        RoutePath.Sample sample = path.sampleAt(path.getTotalMeters() * 0.75);
        assertEquals(-179.9995, sample.point.lng, 1e-7);
        assertEquals(90.0, sample.bearingDegrees, 1e-6);
        assertEquals(-179.999, path.sampleAt(path.getTotalMeters()).point.lng, 1e-7);
    }

    @Test
    public void crossesDateLineWestwardWithoutJumpingToGreenwich() {
        RoutePath path = new RoutePath(Arrays.asList(
                new PointD(0, -179.999), new PointD(0, 179.999)));

        assertEquals(-179.9995, path.sampleAt(path.getTotalMeters() * 0.25).point.lng, 1e-7);
        assertEquals(180.0, Math.abs(path.sampleAt(path.getTotalMeters() * 0.5).point.lng), 1e-7);
        RoutePath.Sample sample = path.sampleAt(path.getTotalMeters() * 0.75);
        assertEquals(179.9995, sample.point.lng, 1e-7);
        assertEquals(270.0, sample.bearingDegrees, 1e-6);
    }
}
