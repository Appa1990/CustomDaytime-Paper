package xyz.mayahive.customdaytime.paper.service;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import xyz.mayahive.customdaytime.common.service.ConfigService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the sleep prerequisite valid for one configured cycle without granting free sleep. */
public final class VillagerSleepService implements Listener {
    private final Plugin plugin;
    private final NamespacedKey actualSleepKey;
    private final NamespacedKey lastWrittenKey;
    private final Map<String, Long> windows;
    private final Map<UUID, Tracked> tracked = new ConcurrentHashMap<>();

    private record Tracked(Villager villager, ScheduledTask task) {}

    public VillagerSleepService(Plugin plugin, ConfigService config) {
        this.plugin = plugin;
        actualSleepKey = new NamespacedKey(plugin, "golem_actual_sleep");
        lastWrittenKey = new NamespacedKey(plugin, "golem_adjusted_sleep");
        Map<String, Long> configured = new HashMap<>();
        for (String world : config.getRootKeys()) {
            if (config.getConfigValue(Boolean.class, true, world, "ironFarmSleepCompensation")) {
                double day = config.getConfigValue(Double.class, 40.0, world, "dayLength");
                double night = config.getConfigValue(Double.class, 10.0, world, "nightLength");
                configured.put(world, VillagerSleepPolicy.windowTicks(day, night));
            }
        }
        windows = Map.copyOf(configured);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Include villagers already loaded before this plugin was enabled.
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(Villager.class).forEach(this::track));
    }

    private void track(Villager villager) {
        tracked.computeIfAbsent(villager.getUniqueId(), id -> {
            ScheduledTask task = villager.getScheduler().runAtFixedRate(plugin,
                    ignored -> update(villager, windows.getOrDefault(villager.getWorld().getKey().asString(),
                            VillagerSleepPolicy.VANILLA_WINDOW)),
                    () -> tracked.remove(id), 1, 20);
            return task == null ? null : new Tracked(villager, task);
        });
    }

    private void update(Villager villager, long window) {
        var data = villager.getPersistentDataContainer();
        Long observed = villager.getMemory(MemoryKey.LAST_SLEPT);
        var result = VillagerSleepPolicy.adjust(villager.getWorld().getGameTime(), observed,
                data.get(actualSleepKey, PersistentDataType.LONG),
                data.get(lastWrittenKey, PersistentDataType.LONG), window);
        if (!java.util.Objects.equals(observed, result.memory())) {
            villager.setMemory(MemoryKey.LAST_SLEPT, result.memory());
        }
        if (result.actualSleep() == null) {
            data.remove(actualSleepKey);
            data.remove(lastWrittenKey);
        } else {
            // Persist the real sleep so unloading/restarting never renews the allowance.
            data.set(actualSleepKey, PersistentDataType.LONG, result.actualSleep());
            data.set(lastWrittenKey, PersistentDataType.LONG, result.lastWritten());
        }
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Villager villager) track(villager);
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        Tracked entry = tracked.remove(event.getEntity().getUniqueId());
        if (entry != null) entry.task().cancel();
    }

    public void stop() {
        tracked.values().forEach(entry -> {
            entry.task().cancel();
            // Paper disables plugins on the owning thread. Never touch another Folia region.
            if (Bukkit.isOwnedByCurrentRegion(entry.villager())) {
                update(entry.villager(), VillagerSleepPolicy.VANILLA_WINDOW);
            }
        });
        tracked.clear();
    }
}
