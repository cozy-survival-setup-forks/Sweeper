package dev.sweeper.player;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player switches for clean-up messages and drop protection. They live in the player's own
 * data, so they survive restarts without any extra file, and are cached while the player is online.
 */
public final class PlayerPrefs implements Listener {

    private static final class Prefs {
        volatile boolean messages = true;
        volatile boolean protection = true;
    }

    private final NamespacedKey messagesOff;
    private final NamespacedKey protectionOff;
    private final Map<UUID, Prefs> cache = new ConcurrentHashMap<>();

    public PlayerPrefs(Plugin plugin) {
        this.messagesOff = new NamespacedKey(plugin, "messages_off");
        this.protectionOff = new NamespacedKey(plugin, "protection_off");
        Bukkit.getOnlinePlayers().forEach(this::load);
    }

    public boolean messagesEnabled(Player player) {
        final Prefs prefs = cache.get(player.getUniqueId());
        return prefs == null || prefs.messages;
    }

    public boolean protectionEnabled(Player player) {
        final Prefs prefs = cache.get(player.getUniqueId());
        return prefs == null || prefs.protection;
    }

    /** Flips the message switch and returns the new state. */
    public boolean toggleMessages(Player player) {
        final Prefs prefs = cache.computeIfAbsent(player.getUniqueId(), id -> new Prefs());
        prefs.messages = !prefs.messages;
        store(player.getPersistentDataContainer(), messagesOff, !prefs.messages);
        return prefs.messages;
    }

    /** Flips the drop protection switch and returns the new state. */
    public boolean toggleProtection(Player player) {
        final Prefs prefs = cache.computeIfAbsent(player.getUniqueId(), id -> new Prefs());
        prefs.protection = !prefs.protection;
        store(player.getPersistentDataContainer(), protectionOff, !prefs.protection);
        return prefs.protection;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    private void load(Player player) {
        final PersistentDataContainer data = player.getPersistentDataContainer();
        final Prefs prefs = new Prefs();
        prefs.messages = !data.has(messagesOff, PersistentDataType.BYTE);
        prefs.protection = !data.has(protectionOff, PersistentDataType.BYTE);
        cache.put(player.getUniqueId(), prefs);
    }

    private static void store(PersistentDataContainer data, NamespacedKey key, boolean off) {
        if (off) {
            data.set(key, PersistentDataType.BYTE, (byte) 1);
        } else {
            data.remove(key);
        }
    }
}
