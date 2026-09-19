package dev.sweeper.sweep;

import dev.sweeper.config.Settings;
import dev.sweeper.message.Messages;
import dev.sweeper.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Supplier;

/**
 * Owns the countdown. One task ticks once a second: it sends the warnings, starts the clean-up at
 * zero and, if enabled, watches the TPS for an extra clean-up.
 */
public final class SweepService {

    private final Plugin plugin;
    private final Supplier<Settings> settings;
    private final Messages messages;
    private final SliceRunner runner;

    private BukkitTask ticker;
    private long remaining;
    private long secondsSinceTpsCheck;
    private long secondsSinceTpsSweep = Long.MAX_VALUE / 2;
    private volatile int lastRemoved;

    public SweepService(Plugin plugin, Supplier<Settings> settings, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.runner = new SliceRunner(plugin, settings);
    }

    /** Starts, or restarts after a reload, the countdown from the full interval. */
    public void start() {
        stopTicker();
        remaining = settings.get().general().interval().toSeconds();
        secondsSinceTpsCheck = 0;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        stopTicker();
        runner.stop();
    }

    public long secondsRemaining() {
        return Math.max(0, remaining);
    }

    public int lastRemoved() {
        return lastRemoved;
    }

    /** Starts a clean-up right now; returns false if one is already running. */
    public boolean sweepNow(Scope scope) {
        return runner.start(scope, this::report);
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void tick() {
        final Settings.General general = settings.get().general();
        remaining--;
        secondsSinceTpsSweep++;

        if (remaining <= 0) {
            sweepNow(Scope.ALL);
            remaining = general.interval().toSeconds();
        } else if (general.warnings().contains(remaining)) {
            messages.broadcast("warning", Messages.text("time", TimeFormat.compact(remaining)));
        }

        if (general.tps().enabled()) {
            checkTps(general.tps());
        }
    }

    private void checkTps(Settings.TpsTrigger trigger) {
        if (++secondsSinceTpsCheck < trigger.checkEvery().toSeconds()) {
            return;
        }
        secondsSinceTpsCheck = 0;

        final double tps = Bukkit.getTPS()[0];
        if (tps < trigger.below() && secondsSinceTpsSweep >= trigger.cooldown().toSeconds()) {
            secondsSinceTpsSweep = 0;
            plugin.getLogger().info("TPS is " + String.format("%.1f", tps) + ", running an extra clean-up");
            sweepNow(Scope.ALL);
        }
    }

    private void report(SweepResult result) {
        lastRemoved = result.total();
        plugin.getLogger().info("Clean-up removed " + result.items() + " items and " + result.entities()
                + " mobs over " + result.ticks() + " ticks");
        if (result.total() == 0 && settings.get().general().silentWhenEmpty()) {
            return;
        }
        messages.broadcast("complete",
                Messages.text("total", result.total()),
                Messages.text("items", result.items()),
                Messages.text("entities", result.entities()));
    }
}
