package dev.sweeper.util;

/**
 * Short countdown text such as {@code 1m 30s}.
 */
public final class TimeFormat {

    private TimeFormat() {
    }

    public static String compact(long totalSeconds) {
        long left = Math.max(0, totalSeconds);
        if (left == 0) {
            return "0s";
        }

        final long hours = left / 3600;
        left %= 3600;
        final long minutes = left / 60;
        final long seconds = left % 60;

        final StringBuilder out = new StringBuilder();
        if (hours > 0) {
            out.append(hours).append("h ");
        }
        if (minutes > 0) {
            out.append(minutes).append("m ");
        }
        if (seconds > 0) {
            out.append(seconds).append('s');
        }
        return out.toString().trim();
    }
}
