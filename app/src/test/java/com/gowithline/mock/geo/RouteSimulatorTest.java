package com.gowithline.mock.geo;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteSimulatorTest {
    private RoutePath straightRoute() {
        return new RoutePath(Arrays.asList(
                new PointD(0, 0),
                new PointD(0, 0.001)));
    }

    @Test
    public void advancesBySpeedTimesTime() {
        RouteSimulator simulator = new RouteSimulator(straightRoute(), 10.0, false);
        simulator.advance(1000);
        assertEquals(10.0, simulator.getTravelledMeters(), 1e-6);
        assertFalse(simulator.isFinished());
        assertEquals(10.0 / simulator.getTotalMeters(), simulator.getProgress(), 1e-6);
    }

    @Test
    public void finishesAtTheEndOfANonLoopingRoute() {
        RouteSimulator simulator = new RouteSimulator(straightRoute(), 10.0, false);
        simulator.advance(60_000);
        assertTrue(simulator.isFinished());
        assertEquals(simulator.getTotalMeters(), simulator.getTravelledMeters(), 1e-6);
        assertEquals(1.0, simulator.getProgress(), 1e-9);

        simulator.advance(1000);
        assertEquals(simulator.getTotalMeters(), simulator.getTravelledMeters(), 1e-6);
    }

    @Test
    public void pauseStopsAdvancing() {
        RouteSimulator simulator = new RouteSimulator(straightRoute(), 10.0, false);
        simulator.advance(1000);
        simulator.pause();
        simulator.advance(5000);
        assertEquals(10.0, simulator.getTravelledMeters(), 1e-6);

        simulator.resume();
        simulator.advance(1000);
        assertEquals(20.0, simulator.getTravelledMeters(), 1e-6);
    }

    @Test
    public void loopsAroundTheRoute() {
        RouteSimulator simulator = new RouteSimulator(straightRoute(), 10.0, true);
        simulator.advance(12_000);
        double expected = (120.0) % simulator.getTotalMeters();
        assertEquals(expected, simulator.getTravelledMeters(), 1e-6);
        assertFalse(simulator.isFinished());
        assertTrue(simulator.getTravelledMeters() < simulator.getTotalMeters());
    }

    @Test
    public void resetRestartsFromTheBeginning() {
        RouteSimulator simulator = new RouteSimulator(straightRoute(), 10.0, false);
        simulator.advance(60_000);
        assertTrue(simulator.isFinished());

        simulator.reset();
        assertFalse(simulator.isFinished());
        assertTrue(simulator.isRunning());
        assertEquals(0.0, simulator.getTravelledMeters(), 1e-12);
    }

    @Test
    public void zeroSpeedDoesNotMove() {
        RouteSimulator simulator = new RouteSimulator(straightRoute(), 0.0, false);
        simulator.advance(10_000);
        assertEquals(0.0, simulator.getTravelledMeters(), 1e-12);
    }
}
