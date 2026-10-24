package com.gowithline.mock.util;

import java.util.Locale;

public final class FormatUtils {
    private FormatUtils() {
    }

    public static String formatDistance(double meters) {
        if (Double.isNaN(meters) || meters < 0) {
            return "--";
        }
        if (meters < 1000.0) {
            return String.format(Locale.US, "%.0f m", meters);
        }
        return String.format(Locale.US, "%.2f km", meters / 1000.0);
    }

    public static String formatDuration(double seconds) {
        if (Double.isNaN(seconds) || seconds < 0) {
            return "--";
        }
        long total = Math.round(seconds);
        if (total < 60) {
            return total + " 秒";
        }
        if (total < 3600) {
            return (total / 60) + " 分 " + (total % 60) + " 秒";
        }
        return (total / 3600) + " 小时 " + ((total % 3600) / 60) + " 分";
    }
}
