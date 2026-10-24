package com.gowithline.mock.geo;

/**
 * Advances a position along a {@link RoutePath} at a constant speed.
 */
public final class RouteSimulator {
    private final RoutePath path;
    private final boolean loop;
    private double speedMps;
    private double travelledMeters;
    private boolean running = true;
    private boolean finished;

    public RouteSimulator(RoutePath path, double speedMps, boolean loop) {
        if (path == null) {
            throw new IllegalArgumentException("path == null");
        }
        this.path = path;
        this.speedMps = Math.max(0.0, speedMps);
        this.loop = loop;
    }

    public void setSpeedMps(double speedMps) {
        this.speedMps = Math.max(0.0, speedMps);
    }

    public double getSpeedMps() {
        return speedMps;
    }

    public boolean isLoop() {
        return loop;
    }

    public double getTravelledMeters() {
        return travelledMeters;
    }

    public double getTotalMeters() {
        return path.getTotalMeters();
    }

    public double getProgress() {
        double total = path.getTotalMeters();
        return total > 0.0 ? Math.min(1.0, travelledMeters / total) : 0.0;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isFinished() {
        return finished;
    }

    public void pause() {
        running = false;
    }

    public void resume() {
        if (!finished) {
            running = true;
        }
    }

    public void reset() {
        travelledMeters = 0.0;
        finished = false;
        running = true;
    }

    /**
     * Advances by the supplied real time. Paused or finished simulators keep
     * the same position.
     */
    public void advance(long deltaMillis) {
        if (!running || finished || deltaMillis <= 0 || speedMps <= 0.0) {
            return;
        }

        double total = path.getTotalMeters();
        if (total <= 0.0) {
            finished = true;
            running = false;
            return;
        }

        double deltaMeters = speedMps * (deltaMillis / 1000.0);
        double next = travelledMeters + deltaMeters;

        if (loop) {
            travelledMeters = next % total;
        } else if (next >= total) {
            travelledMeters = total;
            finished = true;
            running = false;
        } else {
            travelledMeters = next;
        }
    }

    public RoutePath.Sample getSample() {
        return path.sampleAt(travelledMeters);
    }

    public RoutePath getPath() {
        return path;
    }
}
