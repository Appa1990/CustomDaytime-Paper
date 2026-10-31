package xyz.mayahive.customdaytime.paper.service;

import org.junit.Test;

import static org.junit.Assert.*;

public class VillagerSleepPolicyTest {
    private static final long SLEEP = 100000;

    @Test public void fortyMinuteDayAllowsGolemsUntilNextFullCycleButNotForever() {
        long window = VillagerSleepPolicy.windowTicks(40, 10);
        assertEquals(60000, window);
        Long memory = SLEEP;
        Long actual = null;
        Long written = null;
        for (long age = 0; age < window; age += 20) {
            long now = SLEEP + age;
            var state = VillagerSleepPolicy.adjust(now, memory, actual, written, window);
            // Check the entire next second, not just the instant the service runs.
            assertTrue(now + 19 - state.memory() < 24000);
            memory = state.memory();
            actual = state.actualSleep();
            written = state.lastWritten();
        }
        var expired = VillagerSleepPolicy.adjust(SLEEP + window, memory, actual, written, window);
        assertEquals(Long.valueOf(SLEEP), expired.memory());
        assertTrue(SLEEP + window - expired.memory() >= 24000);
        assertNull(expired.actualSleep());
        var later = VillagerSleepPolicy.adjust(SLEEP + 120000, expired.memory(), null, null, window);
        assertEquals(Long.valueOf(SLEEP), later.memory());
    }

    @Test public void neverSleepingDoesNotGrantGolemEligibility() {
        var state = VillagerSleepPolicy.adjust(1000000, null, null, null, 60000);
        assertNull(state.memory());
        assertNull(state.actualSleep());
    }

    @Test public void realNewSleepReplacesCompensatedTimestamp() {
        var old = VillagerSleepPolicy.adjust(SLEEP + 30000, SLEEP, null, null, 60000);
        long newSleep = SLEEP + 31000;
        var fresh = VillagerSleepPolicy.adjust(newSleep, newSleep, old.actualSleep(), old.lastWritten(), 60000);
        assertEquals(Long.valueOf(newSleep), fresh.memory());
        assertNull(fresh.actualSleep());
        var next = VillagerSleepPolicy.adjust(newSleep + 30000, fresh.memory(), null, null, 60000);
        assertEquals(Long.valueOf(newSleep), next.actualSleep());
    }

    @Test public void persistedStateSurvivesUnloadingWithoutRenewingActualSleep() {
        var saved = VillagerSleepPolicy.adjust(SLEEP + 30000, SLEEP, null, null, 60000);
        var loaded = VillagerSleepPolicy.adjust(SLEEP + 59000, saved.memory(),
                saved.actualSleep(), saved.lastWritten(), 60000);
        assertEquals(Long.valueOf(SLEEP), loaded.actualSleep());
        var expired = VillagerSleepPolicy.adjust(SLEEP + 61000, loaded.memory(),
                loaded.actualSleep(), loaded.lastWritten(), 60000);
        assertEquals(Long.valueOf(SLEEP), expired.memory());
        assertNull(expired.actualSleep());
    }

    @Test public void disablingOrShorteningRestoresActualSleep() {
        var saved = VillagerSleepPolicy.adjust(SLEEP + 30000, SLEEP, null, null, 60000);
        for (long window : new long[]{24000, 26000}) {
            var restored = VillagerSleepPolicy.adjust(SLEEP + 30020, saved.memory(),
                    saved.actualSleep(), saved.lastWritten(), window);
            assertEquals(Long.valueOf(SLEEP), restored.memory());
            assertNull(restored.actualSleep());
        }
    }

    @Test public void vanillaAndShortCyclesKeepVanillaSleepRequirement() {
        assertEquals(24000, VillagerSleepPolicy.windowTicks(10, 10));
        assertEquals(24000, VillagerSleepPolicy.windowTicks(5, 5));
        var state = VillagerSleepPolicy.adjust(SLEEP + 24000, SLEEP, null, null, 24000);
        assertEquals(Long.valueOf(SLEEP), state.memory());
        assertNull(state.actualSleep());
    }

    @Test public void invalidDurationsUseSameFallbacksAsTimeController() {
        assertEquals(60000, VillagerSleepPolicy.windowTicks(0, -1));
        assertEquals(60000, VillagerSleepPolicy.windowTicks(Double.NaN, Double.POSITIVE_INFINITY));
        assertEquals(72000, VillagerSleepPolicy.windowTicks(40, 20));
    }

    @Test public void clearedMemoriesAndFutureTimestampsAreNotFabricated() {
        var saved = VillagerSleepPolicy.adjust(SLEEP + 30000, SLEEP, null, null, 60000);
        var cleared = VillagerSleepPolicy.adjust(SLEEP + 30020, null,
                saved.actualSleep(), saved.lastWritten(), 60000);
        assertNull(cleared.memory());
        assertNull(cleared.actualSleep());
        var future = VillagerSleepPolicy.adjust(SLEEP - 1, SLEEP, null, null, 60000);
        assertEquals(Long.valueOf(SLEEP), future.memory());
        assertNull(future.actualSleep());
    }
}
