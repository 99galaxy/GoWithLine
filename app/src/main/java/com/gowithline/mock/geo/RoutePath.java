package com.gowithline.mock.geo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered polyline with pre-computed cumulative distances and interpolation
 * by travelled distance.
 */
public final class RoutePath {
    private static final double MIN_SEGMENT_METERS = 0.01;
    private static final double BEARING_LOOK_BEHIND_METERS = 2.0;
    private static final double BEARING_LOOK_AHEAD_METERS = 3.0;

    private final List<PointD> points;
    private final double[] cumulativeMeters;
    private final double totalMeters;

    public RoutePath(List<PointD> input) {
        ArrayList<PointD> filtered = new ArrayList<>();
        if (input != null) {
            for (PointD point : input) {
                if (point == null) {
                    continue;
                }
                if (filtered.isEmpty()
                        || GeoMath.distanceMeters(filtered.get(filtered.size() - 1), point)
                        >= MIN_SEGMENT_METERS) {
                    filtered.add(point);
                }
            }
        }
        if (filtered.isEmpty()) {
            throw new IllegalArgumentException("route must contain at least one point");
        }
        this.points = Collections.unmodifiableList(filtered);

        cumulativeMeters = new double[filtered.size()];
        double total = 0.0;
        for (int i = 1; i < filtered.size(); i++) {
            total += GeoMath.distanceMeters(filtered.get(i - 1), filtered.get(i));
            cumulativeMeters[i] = total;
        }
        totalMeters = total;
    }

    public List<PointD> getPoints() {
        return points;
    }

    public double getTotalMeters() {
        return totalMeters;
    }

    public boolean isUsable() {
        return points.size() >= 2 && totalMeters >= 0.5;
    }

    public Sample sampleAt(double distanceMeters) {
        double distance = Math.max(0.0, Math.min(totalMeters, distanceMeters));
        PointD point = interpolate(distance);

        double bearing;
        if (totalMeters < MIN_SEGMENT_METERS || points.size() < 2) {
            bearing = 0.0;
        } else {
            double fromDistance = Math.max(0.0, distance - BEARING_LOOK_BEHIND_METERS);
            double toDistance = Math.min(totalMeters, distance + BEARING_LOOK_AHEAD_METERS);
            PointD from = interpolate(fromDistance);
            PointD to = interpolate(toDistance);
            if (GeoMath.distanceMeters(from, to) < MIN_SEGMENT_METERS) {
                bearing = segmentBearing(distance);
            } else {
                bearing = GeoMath.bearingDegrees(from, to);
            }
        }

        return new Sample(point, bearing, segmentIndexAt(distance));
    }

    private PointD interpolate(double distance) {
        if (points.size() == 1 || totalMeters <= 0.0) {
            return points.get(0);
        }
        int segment = segmentIndexAt(distance);
        if (segment >= points.size() - 1) {
            return points.get(points.size() - 1);
        }
        double segmentStart = cumulativeMeters[segment];
        double segmentLength = cumulativeMeters[segment + 1] - segmentStart;
        if (segmentLength <= 0.0) {
            return points.get(segment);
        }
        double ratio = (distance - segmentStart) / segmentLength;
        ratio = Math.max(0.0, Math.min(1.0, ratio));

        PointD a = points.get(segment);
        PointD b = points.get(segment + 1);
        return new PointD(
                a.lat + (b.lat - a.lat) * ratio,
                a.lng + (b.lng - a.lng) * ratio);
    }

    private double segmentBearing(double distance) {
        int segment = segmentIndexAt(distance);
        if (segment >= points.size() - 1) {
            segment = points.size() - 2;
        }
        if (segment < 0) {
            return 0.0;
        }
        return GeoMath.bearingDegrees(points.get(segment), points.get(segment + 1));
    }

    /** Binary search for the segment containing {@code distance}. */
    private int segmentIndexAt(double distance) {
        if (points.size() < 2) {
            return 0;
        }
        if (distance <= 0.0) {
            return 0;
        }
        if (distance >= totalMeters) {
            return points.size() - 2;
        }
        int low = 0;
        int high = points.size() - 2;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (cumulativeMeters[mid + 1] < distance) {
                low = mid + 1;
            } else if (cumulativeMeters[mid] > distance) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return Math.max(0, Math.min(points.size() - 2, low));
    }

    public static final class Sample {
        public final PointD point;
        public final double bearingDegrees;
        public final int segmentIndex;

        public Sample(PointD point, double bearingDegrees, int segmentIndex) {
            this.point = point;
            this.bearingDegrees = bearingDegrees;
            this.segmentIndex = segmentIndex;
        }
    }
}
