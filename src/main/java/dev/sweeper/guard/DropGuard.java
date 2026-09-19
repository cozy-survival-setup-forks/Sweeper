package dev.sweeper.guard;

import dev.sweeper.config.Settings;
import dev.sweeper.message.Messages;
import dev.sweeper.player.PlayerPrefs;
import dev.sweeper.sweep.SweepService;
import dev.sweeper.util.TimeFormat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Blocks item drops shortly before a clean-up so players don't throw something away just as it
 * would be swept up.
 */
public final class DropGuard implements Listener {

    public static final String BYPASS = "sweeper.bypass.dropguard";

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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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

        event.setCancelled(true);
        final long now = System.currentTimeMillis();
        final Long previous = lastNotice.get(player.getUniqueId());
        if (previous == null || now - previous >= NOTICE_GAP_MILLIS) {
            lastNotice.put(player.getUniqueId(), now);
            messages.send(player, "drop_blocked", Messages.text("time", TimeFormat.compact(left)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastNotice.remove(event.getPlayer().getUniqueId());
    }
}
