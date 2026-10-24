package com.gowithline.mock.service;

/**
 * Immutable state published by {@link MockLocationService} to the UI. All
 * coordinates are WGS-84; the activity converts them to the active map datum.
 */
public final class SimulationSnapshot {
    public static final int STATE_IDLE = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_FINISHED = 3;
    public static final int STATE_ERROR = 4;

    public static final int MODE_ROUTE = 0;
    public static final int MODE_JOYSTICK = 1;

    public final int state;
    public final int mode;
    public final double lat;
    public final double lng;
    public final double bearingDegrees;
    public final double speedMps;
    public final double travelledMeters;
    public final double totalMeters;
    public final double progress;
    public final boolean loop;
    public final String error;
    public final long revision;

    public SimulationSnapshot(int state, int mode, double lat, double lng,
                              double bearingDegrees, double speedMps,
                              double travelledMeters, double totalMeters,
                              double progress, boolean loop, String error,
                              long revision) {
        this.state = state;
        this.mode = mode;
        this.lat = lat;
        this.lng = lng;
        this.bearingDegrees = bearingDegrees;
        this.speedMps = speedMps;
        this.travelledMeters = travelledMeters;
        this.totalMeters = totalMeters;
        this.progress = progress;
        this.loop = loop;
        this.error = error;
        this.revision = revision;
    }

    public static SimulationSnapshot idle() {
        return new SimulationSnapshot(STATE_IDLE, MODE_ROUTE, Double.NaN, Double.NaN,
                0.0, 0.0, 0.0, 0.0, 0.0, false, null, 0L);
    }

    public static SimulationSnapshot error(String message) {
        return new SimulationSnapshot(STATE_ERROR, MODE_ROUTE, Double.NaN, Double.NaN,
                0.0, 0.0, 0.0, 0.0, 0.0, false, message, 0L);
    }

    public boolean isActive() {
        return state == STATE_RUNNING || state == STATE_PAUSED || state == STATE_FINISHED;
    }

    public boolean hasPosition() {
        return !Double.isNaN(lat) && !Double.isNaN(lng);
    }
}
