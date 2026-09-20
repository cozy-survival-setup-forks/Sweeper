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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class SweeperPlugin extends JavaPlugin {

    private volatile Settings settings;
    private Messages messages;
    private SweepService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig(), getLogger());

        Perms.register(getServer().getPluginManager());

        final PlayerPrefs prefs = new PlayerPrefs(this);
        messages = new Messages(this, prefs);
        messages.load();
        service = new SweepService(this, () -> settings, messages);

        getServer().getPluginManager().registerEvents(prefs, this);
        getServer().getPluginManager().registerEvents(new dev.sweeper.guard.DeathDrops(), this);
        getServer().getPluginManager().registerEvents(new DropGuard(() -> settings, service, prefs, messages), this);

        final SweeperCommand command = new SweeperCommand(this, service, prefs, messages);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build().build(), "Timed clean-ups for drops and mobs",
                        List.of("sweep")));

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PapiHook(this, service).register();
        }

        service.start();
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.stop();
        }
    }

    /** Re-reads config.yml and lang.yml and restarts the countdown. */
    public void reload() {
        reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        messages.load();
        service.start();
    }
}
