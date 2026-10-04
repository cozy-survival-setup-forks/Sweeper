package dev.sweeper;

import dev.sweeper.command.Perms;
import dev.sweeper.command.SweeperCommand;
import dev.sweeper.config.Settings;
import dev.sweeper.guard.DropGuard;
import dev.sweeper.hook.PapiHook;
import dev.sweeper.message.Messages;
import dev.sweeper.player.PlayerPrefs;
import dev.sweeper.sweep.SweepService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class SweeperPlugin extends JavaPlugin {

    private volatile Settings settings;
    private Messages messages;
    private SweepService service;
    private PapiHook papi;

    @Override
    public void onEnable() {
        try {
            enableInner();
        } catch (RuntimeException e) {
            getLogger().severe("Sweeper could not start: " + e.getMessage() + ". Check config.yml and lang.yml.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void enableInner() {
        saveDefaultConfig();
        settings = loadSettings();

        Perms.register(getServer().getPluginManager());

        final PlayerPrefs prefs = new PlayerPrefs(this);
        messages = new Messages(this, prefs);
        if (!messages.load()) {
            throw new IllegalStateException("lang.yml could not be read");
        }
        service = new SweepService(this, () -> settings, messages);

        getServer().getPluginManager().registerEvents(prefs, this);
        getServer().getPluginManager().registerEvents(new dev.sweeper.guard.DeathDrops(), this);
        getServer().getPluginManager().registerEvents(new DropGuard(() -> settings, service, prefs, messages), this);

        final SweeperCommand command = new SweeperCommand(this, service, prefs, messages);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build().build(), "Timed clean-ups for drops and mobs",
                        List.of("sweep")));

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            papi = new PapiHook(this, service);
            papi.register();
        }

        service.start();
        Metrics.start(this);
        Banner.print(this, "Thanks for keeping the server light.");
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.stop();
        }
        if (papi != null) {
            papi.unregister();
        }
    }

    /** A file that cannot be read stops the plugin from starting; a reload of it just keeps the old values. */
    private Settings loadSettings() {
        final YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(new File(getDataFolder(), "config.yml"));
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("config.yml could not be read: " + e.getMessage(), e);
        }
        return Settings.load(cfg, getLogger());
    }

    /** Re-reads config.yml and lang.yml and restarts the countdown. Returns false and keeps the old values on a bad file. */
    public boolean reload() {
        final Settings fresh;
        try {
            fresh = loadSettings();
        } catch (RuntimeException e) {
            getLogger().severe(e.getMessage() + " - keeping the old settings");
            return false;
        }
        if (!messages.load()) {
            return false;
        }
        settings = fresh;
        service.start();
        return true;
    }
}
