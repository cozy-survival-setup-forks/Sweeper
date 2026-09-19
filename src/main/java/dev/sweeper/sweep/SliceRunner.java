package dev.sweeper.sweep;

import dev.sweeper.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs a clean-up in slices. Each tick works through entities until its time budget is spent,
 * then carries on next tick, so thousands of drops never turn into one long freeze.
 */
final class SliceRunner {

    private static final int CLOCK_CHECK_MASK = 31;

    private final Plugin plugin;
    private final Supplier<Settings> settings;
    private BukkitTask task;

    SliceRunner(Plugin plugin, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    boolean isRunning() {
        return task != null;
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Starts a clean-up; returns false if one is already in progress. */
    boolean start(Scope scope, Consumer<SweepResult> done) {
        if (task != null) {
            return false;
        }
        final Run run = new Run(settings.get(), scope, done);
        task = Bukkit.getScheduler().runTaskTimer(plugin, run, 1L, 1L);
        return true;
    }

    private enum Kind { ITEMS, MOBS }

    private record Job(World world, Kind kind) {
    }

    private final class Run implements Runnable {
        private final long budgetNanos;
        private final Consumer<SweepResult> done;
        private final ItemRules itemRules;
        private final EntityRules entityRules;
        private final Queue<Job> jobs = new ArrayDeque<>();

        private Kind kind = Kind.ITEMS;
        private Iterator<? extends Entity> current;
        private int items;
        private int mobs;
        private long ticks;

        Run(Settings config, Scope scope, Consumer<SweepResult> done) {
            this.budgetNanos = config.general().budgetNanos();
            this.done = done;

            final boolean doItems = scope.includesItems() && config.items().enabled();
            final boolean doMobs = scope.includesEntities() && config.entities().enabled();
            this.itemRules = doItems ? new ItemRules(config.items()) : null;
            this.entityRules = doMobs ? new EntityRules(config.entities()) : null;

            for (World world : Bukkit.getWorlds()) {
                final String name = world.getName().toLowerCase(Locale.ROOT);
                if (doItems && !config.items().disabledWorlds().contains(name)) {
                    jobs.add(new Job(world, Kind.ITEMS));
                }
                if (doMobs && !config.entities().disabledWorlds().contains(name)) {
                    jobs.add(new Job(world, Kind.MOBS));
                }
            }
        }

        @Override
        public void run() {
            ticks++;
            try {
                if (step()) {
                    finish();
                }
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Clean-up stopped early: " + e);
                finish();
            }
        }

        /** Works until the tick budget is spent; returns true once every job is done. */
        private boolean step() {
            final long deadline = System.nanoTime() + budgetNanos;
            int handled = 0;
            while (true) {
                if (current == null || !current.hasNext()) {
                    if (!nextJob()) {
                        return true;
                    }
                    if (System.nanoTime() >= deadline) {
                        return false;
                    }
                    continue;
                }

                final Entity entity = current.next();
                if (entity.isValid()) {
                    consider(entity);
                }
                if ((++handled & CLOCK_CHECK_MASK) == 0 && System.nanoTime() >= deadline) {
                    return false;
                }
            }
        }

        private void consider(Entity entity) {
            if (kind == Kind.ITEMS) {
                if (entity instanceof Item item && itemRules.shouldRemove(item)) {
                    item.remove();
                    items++;
                }
            } else if (entity instanceof Mob mob && entityRules.shouldRemove(mob)) {
                mob.remove();
                mobs++;
            }
        }

        private boolean nextJob() {
            final Job job = jobs.poll();
            if (job == null) {
                current = null;
                return false;
            }
            kind = job.kind();
            current = kind == Kind.ITEMS
                    ? job.world().getEntitiesByClass(Item.class).iterator()
                    : job.world().getEntitiesByClass(Mob.class).iterator();
            return true;
        }

        private void finish() {
            stop();
            done.accept(new SweepResult(items, mobs, ticks));
        }
    }
}
