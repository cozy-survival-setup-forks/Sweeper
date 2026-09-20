package dev.sweeper.guard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks what a player drops when they die, so a clean-up never removes someone's belongings just after
 * they died. Those items still disappear on their own after the normal five minutes.
 */
public final class DeathDrops implements Listener {

    /** Items with this mark are never swept. */
    public static final NamespacedKey KEEP = NamespacedKey.fromString("sweeper:death_drop");

    private record Death(Location where, int tick) {
    }

    private final List<Death> recent = new ArrayList<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getDrops().isEmpty()) {
            return;
        }
        final int now = Bukkit.getCurrentTick();
        recent.removeIf(death -> now - death.tick() > 2);
        recent.add(new Death(event.getEntity().getLocation(), now));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(ItemSpawnEvent event) {
        if (recent.isEmpty()) {
            return;
        }
        final int now = Bukkit.getCurrentTick();
        recent.removeIf(death -> now - death.tick() > 2);

        final Item item = event.getEntity();
        for (Death death : recent) {
            if (death.where().getWorld() == item.getWorld() && death.where().distanceSquared(item.getLocation()) <= 9) {
                item.getPersistentDataContainer().set(KEEP, PersistentDataType.BYTE, (byte) 1);
                return;
            }
        }
    }
}
