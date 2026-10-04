package dev.sweeper.guard;

import dev.sweeper.config.Settings;
import dev.sweeper.message.Messages;
import dev.sweeper.player.PlayerPrefs;
import dev.sweeper.sweep.SweepService;
import dev.sweeper.util.TimeFormat;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Keeps what a player drops shortly before a clean-up out of that clean-up. Drops are never cancelled, since a
 * cancelled drop is handed back to the inventory and whatever does not fit is lost.
 */
public final class DropGuard implements Listener {

    public static final String BYPASS = "sweeper.bypass.dropguard";

    /** Items dropped just before a clean-up carry the number of that clean-up and are skipped by it. */
    public static final NamespacedKey KEEP_FOR = NamespacedKey.fromString("sweeper:kept_for");

    private static final long NOTICE_GAP_MILLIS = 1500;

    private final Supplier<Settings> settings;
    private final SweepService service;
    private final PlayerPrefs prefs;
    private final Messages messages;
    private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();

    public DropGuard(Supplier<Settings> settings, SweepService service, PlayerPrefs prefs, Messages messages) {
        this.settings = settings;
        this.service = service;
        this.prefs = prefs;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        final Settings.DropGuard guard = settings.get().general().dropGuard();
        if (!guard.enabled()) {
            return;
        }

        final long left = service.secondsRemaining();
        if (left > guard.duration().toSeconds()) {
            return;
        }

        final Player player = event.getPlayer();
        if (!prefs.protectionEnabled(player) || player.hasPermission(BYPASS)) {
            return;
        }

        event.getItemDrop().getPersistentDataContainer().set(KEEP_FOR, PersistentDataType.INTEGER, service.cycle());
        final long now = System.currentTimeMillis();
        final Long previous = lastNotice.get(player.getUniqueId());
        if (previous == null || now - previous >= NOTICE_GAP_MILLIS) {
            lastNotice.put(player.getUniqueId(), now);
            messages.send(player, "drop_kept", Messages.text("time", TimeFormat.compact(left)));
        }
    }

    /** A stack that absorbs a kept one stays kept, whichever of the two entities survives the merge. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        final var from = event.getEntity().getPersistentDataContainer();
        final var into = event.getTarget().getPersistentDataContainer();
        if (from.has(DeathDrops.KEEP)) {
            into.set(DeathDrops.KEEP, PersistentDataType.BYTE, (byte) 1);
        }
        final Integer kept = from.get(KEEP_FOR, PersistentDataType.INTEGER);
        final Integer current = into.get(KEEP_FOR, PersistentDataType.INTEGER);
        if (kept != null && (current == null || current < kept)) {
            into.set(KEEP_FOR, PersistentDataType.INTEGER, kept);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastNotice.remove(event.getPlayer().getUniqueId());
    }
}
