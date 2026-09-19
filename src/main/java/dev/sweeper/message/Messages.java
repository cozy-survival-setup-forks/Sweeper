package dev.sweeper.message;

import dev.sweeper.player.PlayerPrefs;
import dev.sweeper.util.Legacy;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads lang.yml and sends its messages as chat, action bar and sound. A message is parsed when
 * it is sent, so placeholders like {@code <time>} are just tag resolvers.
 */
public final class Messages {

    private record Entry(boolean enabled, boolean chat, boolean actionBar, String chatText,
                         String actionBarText, Sound sound) {
    }

    private final JavaPlugin plugin;
    private final PlayerPrefs prefs;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<String, Entry> entries = new HashMap<>();
    private String enabledWord = "enabled";
    private String disabledWord = "disabled";

    public Messages(JavaPlugin plugin, PlayerPrefs prefs) {
        this.plugin = plugin;
        this.prefs = prefs;
    }

    public void load() {
        final File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }

        final YamlConfiguration lang = YamlConfiguration.loadConfiguration(file);
        try (Reader defaults = new InputStreamReader(plugin.getResource("lang.yml"), StandardCharsets.UTF_8)) {
            lang.setDefaults(YamlConfiguration.loadConfiguration(defaults));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read the bundled lang.yml: " + e.getMessage());
        }

        entries.clear();
        for (String key : lang.getKeys(false)) {
            final ConfigurationSection section = lang.getConfigurationSection(key);
            if (section != null && !key.equals("states")) {
                entries.put(key, readEntry(key, section));
            }
        }
        enabledWord = lang.getString("states.enabled", "enabled");
        disabledWord = lang.getString("states.disabled", "disabled");
    }

    private Entry readEntry(String key, ConfigurationSection section) {
        final List<String> types = types(section.get("type"));
        Sound sound = null;
        final ConfigurationSection soundSection = section.getConfigurationSection("sound");
        if (soundSection != null && soundSection.getBoolean("enabled", true)) {
            try {
                sound = Sound.sound(
                        Key.key(soundSection.getString("key", "minecraft:block.note_block.pling")),
                        Sound.Source.valueOf(soundSection.getString("source", "MASTER").toUpperCase(Locale.ROOT)),
                        (float) soundSection.getDouble("volume", 1.0),
                        (float) soundSection.getDouble("pitch", 1.0));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("lang.yml: bad sound for '" + key + "', it will play no sound");
            }
        }
        return new Entry(
                section.getBoolean("enabled", true),
                types.contains("chat"),
                types.contains("actionbar"),
                Legacy.toMiniMessage(section.getString("chat", "")),
                Legacy.toMiniMessage(section.getString("actionbar", "")),
                sound);
    }

    /** Accepts both {@code [chat, actionbar]} and the comma separated form {@code chat, actionbar}. */
    private static List<String> types(Object raw) {
        final String joined = raw instanceof List<?> list
                ? String.join(",", list.stream().map(String::valueOf).toList())
                : String.valueOf(raw);
        return java.util.Arrays.stream(joined.toLowerCase(Locale.ROOT).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** The coloured word for a switch, ready to use as a {@code <state>} placeholder. */
    public TagResolver state(boolean on) {
        return TagResolver.resolver("state", (args, ctx) -> net.kyori.adventure.text.minimessage.tag.Tag.inserting(
                mini.deserialize(Legacy.toMiniMessage(on ? enabledWord : disabledWord))));
    }

    public static TagResolver text(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** Sends to everyone online who has not switched messages off, and to the console. */
    public void broadcast(String key, TagResolver... resolvers) {
        final Entry entry = entries.get(key);
        if (entry == null || !entry.enabled()) {
            return;
        }

        final Component chat = entry.chat() ? render(entry.chatText(), resolvers) : null;
        final Component bar = entry.actionBar() ? render(entry.actionBarText(), resolvers) : null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (prefs.messagesEnabled(player)) {
                deliver(player, entry, chat, bar);
            }
        }
        if (chat != null) {
            Bukkit.getConsoleSender().sendMessage(chat);
        }
    }

    /** Sends to a single sender, whatever their message switch says. */
    public void send(CommandSender to, String key, TagResolver... resolvers) {
        final Entry entry = entries.get(key);
        if (entry == null || !entry.enabled()) {
            return;
        }

        final Component chat = entry.chat() ? render(entry.chatText(), resolvers) : null;
        if (to instanceof Player player) {
            final Component bar = entry.actionBar() ? render(entry.actionBarText(), resolvers) : null;
            deliver(player, entry, chat, bar);
        } else if (chat != null) {
            to.sendMessage(chat);
        }
    }

    private void deliver(Player player, Entry entry, Component chat, Component bar) {
        if (chat != null) {
            player.sendMessage(chat);
        }
        if (bar != null) {
            player.sendActionBar(bar);
        }
        if (entry.sound() != null) {
            player.playSound(entry.sound());
        }
    }

    private Component render(String text, TagResolver... resolvers) {
        return mini.deserialize(text, TagResolver.resolver(resolvers));
    }
}
