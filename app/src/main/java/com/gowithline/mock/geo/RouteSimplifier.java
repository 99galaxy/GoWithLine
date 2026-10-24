package com.gowithline.mock.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * Ramer-Douglas-Peucker simplification in a local metric plane.
 */
public final class RouteSimplifier {
    private RouteSimplifier() {
    }

    public static List<PointD> simplify(List<PointD> input, double epsilonMeters) {
        List<PointD> points = removeDuplicates(input);
        if (points.size() <= 2 || epsilonMeters <= 0) {
            return points;
        }

        int n = points.size();
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;

        // Local equirectangular projection around the first point.
        double lat0 = points.get(0).lat;
        double mPerLng = GeoMath.metersPerDegreeLng(lat0);
        double mPerLat = GeoMath.metersPerDegreeLat();
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i).lng * mPerLng;
            ys[i] = points.get(i).lat * mPerLat;
        }

        ArrayList<int[]> stack = new ArrayList<>();
        stack.add(new int[]{0, n - 1});
        while (!stack.isEmpty()) {
            int[] range = stack.remove(stack.size() - 1);
            int first = range[0];
            int last = range[1];
            if (last <= first + 1) {
                continue;
            }

            double maxDistance = -1.0;
            int farthest = -1;
            for (int i = first + 1; i < last; i++) {
                double distance = pointToSegmentDistance(
                        xs[i], ys[i], xs[first], ys[first], xs[last], ys[last]);
                if (distance > maxDistance) {
                    maxDistance = distance;
                    farthest = i;
                }
            }

            if (maxDistance > epsilonMeters && farthest > first) {
                keep[farthest] = true;
                stack.add(new int[]{first, farthest});
                stack.add(new int[]{farthest, last});
            }
        }

        ArrayList<PointD> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    private static List<PointD> removeDuplicates(List<PointD> input) {
        ArrayList<PointD> result = new ArrayList<>();
        if (input == null) {
            return result;
        }
        for (PointD point : input) {
            if (point == null) {
                continue;
            }
            if (result.isEmpty()
                    || GeoMath.distanceMeters(result.get(result.size() - 1), point) > 0.01) {
                result.add(point);
            }
        }
        return result;
    }

    private static double pointToSegmentDistance(
            double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 1e-9) {
            return Math.hypot(px - ax, py - ay);
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double projectionX = ax + t * dx;
        double projectionY = ay + t * dy;
        return Math.hypot(px - projectionX, py - projectionY);
    }
}
