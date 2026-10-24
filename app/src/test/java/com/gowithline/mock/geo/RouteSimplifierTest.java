package com.gowithline.mock.geo;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RouteSimplifierTest {
    @Test
    public void collinearPointsCollapseToEndpoints() {
        List<PointD> input = Arrays.asList(
                new PointD(0, 0),
                new PointD(0, 0.001),
                new PointD(0, 0.002),
                new PointD(0, 0.003));
        List<PointD> result = RouteSimplifier.simplify(input, 1.0);
        assertEquals(2, result.size());
        assertEquals(0.0, result.get(0).lng, 1e-12);
        assertEquals(0.003, result.get(1).lng, 1e-12);
    }

    @Test
    public void cornerIsPreserved() {
        List<PointD> input = Arrays.asList(
                new PointD(0, 0),
                new PointD(0, 0.001),
                new PointD(0.001, 0.001));
        List<PointD> result = RouteSimplifier.simplify(input, 1.0);
        assertEquals(3, result.size());
    }

    @Test
    public void smallNoiseIsRemoved() {
        List<PointD> input = Arrays.asList(
                new PointD(0, 0),
                new PointD(0.000005, 0.0005),
                new PointD(0, 0.001));
        List<PointD> result = RouteSimplifier.simplify(input, 5.0);
        assertEquals(2, result.size());
    }

    @Test
    public void duplicatePointsAreRemoved() {
        List<PointD> input = Arrays.asList(
                new PointD(30, 120),
                new PointD(30, 120),
                new PointD(30, 120.001),
                new PointD(30, 120.001));
        List<PointD> result = RouteSimplifier.simplify(input, 0.0);
        assertEquals(2, result.size());
    }
}
