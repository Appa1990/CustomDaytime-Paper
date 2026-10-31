package xyz.mayahive.customdaytime.common.world;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xyz.mayahive.customdaytime.api.model.WorldKey;
import xyz.mayahive.customdaytime.api.platform.*;
import xyz.mayahive.customdaytime.common.context.CustomDaytimeContext;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.Assert.*;

public class WorldTimeControllerTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private long time;
    private Runnable tick;
    private boolean advance = true;
    private final WorldKey key = new WorldKey("minecraft", "overworld");

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> call.apply(method.getName(), args));
    }

    private void start(String config) throws Exception {
        Files.writeString(folder.getRoot().toPath().resolve("config.conf"), config);
        PlatformWorld world = stub(PlatformWorld.class, (name, args) -> switch (name) {
            case "key" -> key;
            case "keyAsString" -> key.asString();
            case "time" -> {
                if (args == null) yield Optional.of(time);
                time = (long) args[0];
                yield true;
            }
            case "playerCount", "sleepingPlayerCount" -> 0;
            case "gameRuleAdvanceTime" -> advance;
            case "gameRulePlayerSleepingPercentage" -> 100;
            default -> throw new AssertionError(name);
        });
        PlatformLogger logger = stub(PlatformLogger.class, (name, args) -> null);
        PlatformScheduler scheduler = stub(PlatformScheduler.class, (name, args) -> {
            if (!name.equals("runRepeating")) throw new AssertionError(name);
            tick = (Runnable) args[0];
            return (PlatformTask) () -> {};
        });
        Platform platform = stub(Platform.class, (name, args) -> switch (name) {
            case "configDirectory" -> folder.getRoot().toPath();
            case "logger" -> logger;
            case "scheduler" -> scheduler;
            case "debug" -> false;
            default -> throw new AssertionError(name);
        });
        CustomDaytimeContext context = new CustomDaytimeContext(platform);
        context.worldCache().registerWorld(world);
        new WorldTimeController(context, key).start();
    }

    private void serverTicks(int count) {
        for (int i = 0; i < count; i++) {
            if (advance) time++; // Vanilla still advances before the plugin runs.
            tick.run();
        }
    }

    @Test public void defaultDayLastsFortyMinutesAndNightTen() throws Exception {
        start("");
        serverTicks(47999);
        assertEquals(11999, time);
        serverTicks(1);
        assertEquals(12000, time);
        serverTicks(11999);
        assertEquals(23999, time);
        serverTicks(1);
        assertEquals(24000, time);
    }

    @Test public void fractionalStepsHoldTimeBetweenAdvances() throws Exception {
        start("");
        for (int i = 0; i < 100; i++) {
            serverTicks(3);
            assertEquals(i, time);
            serverTicks(1);
            assertEquals(i + 1, time);
        }
    }

    @Test public void invalidDurationsFallBackToDefaults() throws Exception {
        start("\"minecraft:overworld\" { dayLength=0, nightLength=-1 }");
        serverTicks(48000 + 12000);
        assertEquals(24000, time);
    }

    @Test public void subsecondDurationsAreNotRoundedToZero() throws Exception {
        start("\"minecraft:overworld\" { dayLength=0.005 }");
        serverTicks(6);
        assertEquals(12000, time);
    }

    @Test public void timeCommandsResynchronizeAndDisabledCyclePauses() throws Exception {
        start("");
        serverTicks(4);
        time = 18000;
        serverTicks(1);
        assertEquals(18002, time);
        advance = false;
        serverTicks(100);
        assertEquals(18002, time);
        advance = true;
        serverTicks(1);
        assertEquals(18003, time);
    }
}
