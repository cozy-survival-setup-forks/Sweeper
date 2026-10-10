package dev.sweeper;

import dev.sweeper.command.Perms;
import dev.sweeper.command.SweeperCommand;
import dev.sweeper.config.Settings;
import dev.sweeper.guard.DropGuard;
import dev.sweeper.hook.PapiHook;
import dev.sweeper.message.Messages;
import dev.sweeper.player.PlayerPrefs;
import dev.sweeper.safe.ConfigMigrator;
import dev.sweeper.safe.Doctor;
import dev.sweeper.safe.FileBackups;
import dev.sweeper.safe.Guard;
import dev.sweeper.safe.Health;
import dev.sweeper.safe.Prep;
import dev.sweeper.safe.ServerId;
import dev.sweeper.sweep.SweepService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SweeperPlugin extends JavaPlugin {

    private static final int CONFIG_VERSION = 1;
    private static final int LANG_VERSION = 1;

    private final List<Prep.Spec> files = List.of(
            new Prep.Spec("config.yml", "config-version", CONFIG_VERSION, Prep.configMigrator(CONFIG_VERSION), null),
            new Prep.Spec("lang.yml", "lang-version", LANG_VERSION, new ConfigMigrator("lang-version", LANG_VERSION), null));

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
        Health.storage("YAML files in the plugin folder (config.yml, lang.yml, data.yml)");
        Prep.startup(this, files);
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
        final boolean beacon = getConfig().getBoolean("metrics.enabled", true);
        Metrics.start(this, ServerId.resolve(getDataFolder().toPath(),
                ServerId.inYaml(new File(getDataFolder(), "data.yml").toPath(), getLogger()), beacon, getLogger()));
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
        final List<Guard.Problem> problems = Prep.validate(this, files);
        if (!problems.isEmpty()) {
            Prep.logRejected(this, problems);
            return false;
        }
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

    /** The text of /sweeper doctor. */
    public List<String> doctor() {
        final List<String> extra = new ArrayList<>(Prep.versionLines(this, files));
        extra.add("Pending writes: 0 (this plugin keeps no queued saves)");
        return Doctor.report(getName(), getPluginMeta().getVersion(), extra);
    }

    /** /sweeper backup now: a verified copy of the settings and data files. */
    public boolean backupNow() {
        final List<String> names = new ArrayList<>(Prep.fileNames(files));
        names.add("data.yml");
        return FileBackups.snapshot(getDataFolder().toPath(), names, 5, getLogger());
    }
}
