package dev.sweeper.hook;

import dev.sweeper.sweep.SweepService;
import dev.sweeper.util.TimeFormat;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * %sweeper_time%, %sweeper_seconds% and %sweeper_last_removed% for scoreboards and tab lists.
 */
public final class PapiHook extends PlaceholderExpansion {

    private final Plugin plugin;
    private final SweepService service;

    public PapiHook(Plugin plugin, SweepService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sweeper";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "time" -> TimeFormat.compact(service.secondsRemaining());
            case "seconds" -> Long.toString(service.secondsRemaining());
            case "last_removed" -> Integer.toString(service.lastRemoved());
            default -> null;
        };
    }
}
