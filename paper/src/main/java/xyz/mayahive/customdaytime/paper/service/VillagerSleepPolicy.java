package xyz.mayahive.customdaytime.paper.service;

/** Pure timing rules; timestamps use game time, never the adjusted daylight clock. */
public final class VillagerSleepPolicy {
    public static final long VANILLA_WINDOW = 24000;
    // The service checks every 20 ticks. Keep enough headroom until the next check.
    private static final long EFFECTIVE_AGE = VANILLA_WINDOW - 40;

    private VillagerSleepPolicy() {}

    public record Adjustment(Long memory, Long actualSleep, Long lastWritten) {}

    public static long windowTicks(double dayMinutes, double nightMinutes) {
        double day = validDuration(dayMinutes) ? dayMinutes : 40.0;
        double night = validDuration(nightMinutes) ? nightMinutes : 10.0;
        return Math.max(VANILLA_WINDOW, (long) Math.ceil(day * 1200.0 + night * 1200.0));
    }

    private static boolean validDuration(double minutes) {
        return Double.isFinite(minutes) && minutes >= 1.0 / 1200.0
                && Double.isFinite(minutes * 1200.0);
    }

    public static Adjustment adjust(long now, Long observed, Long actualSleep,
                                    Long lastWritten, long window) {
        if (observed == null) return new Adjustment(null, null, null);
        // A changed memory means a real new sleep (or another plugin's update).
        long actual = actualSleep != null && observed.equals(lastWritten) ? actualSleep : observed;
        long age = now - actual;
        if (window <= VANILLA_WINDOW || age < EFFECTIVE_AGE || age >= window || age < 0) {
            return new Adjustment(actual, null, null);
        }
        long effectiveSleep = now - EFFECTIVE_AGE;
        return new Adjustment(effectiveSleep, actual, effectiveSleep);
    }
}
