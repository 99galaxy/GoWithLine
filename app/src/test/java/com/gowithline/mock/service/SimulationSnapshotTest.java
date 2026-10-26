package com.gowithline.mock.service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimulationSnapshotTest {
    @Test
    public void finishedRouteCanRestartWhileStillHoldingItsPosition() {
        SimulationSnapshot finished = snapshot(SimulationSnapshot.STATE_FINISHED);

        assertTrue(finished.canStart());
        assertTrue(finished.isActive());
        assertTrue(finished.hasPosition());
    }

    @Test
    public void runningAndPausedSimulationsCannotBeReplaced() {
        assertFalse(snapshot(SimulationSnapshot.STATE_RUNNING).canStart());
        assertFalse(snapshot(SimulationSnapshot.STATE_PAUSED).canStart());
    }

    @Test
    public void idleAndFailedSimulationsAllowStarting() {
        assertTrue(SimulationSnapshot.idle().canStart());
        assertTrue(SimulationSnapshot.error("injection failed").canStart());
    }

    private static SimulationSnapshot snapshot(int state) {
        return new SimulationSnapshot(state, SimulationSnapshot.MODE_ROUTE,
                31.2, 121.5, 90.0, 1.4, 100.0, 100.0, 1.0,
                false, null, 1L);
    }
}
