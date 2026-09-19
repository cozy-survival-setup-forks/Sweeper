package dev.sweeper.config;

import dev.sweeper.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Everything from config.yml, parsed once per reload so the clean-up itself never touches YAML.
 */
public record Settings(General general, Items items, Entities entities) {

    public enum Mode { WHITELIST, BLACKLIST }

    public record General(Duration interval, List<Long> warnings, long budgetNanos, boolean silentWhenEmpty,
                          DropGuard dropGuard, TpsTrigger tps) {
    }

    public record DropGuard(boolean enabled, Duration duration) {
    }

    public record TpsTrigger(boolean enabled, double below, Duration checkEvery, Duration cooldown) {
    }

    public record ItemFlags(boolean named, boolean metadata, boolean owner) {
    }

    public record EntityFlags(boolean named, boolean metadata, boolean team, boolean leashed, boolean tamed,
                              boolean bred, boolean equipped, boolean vehicles) {
    }

    public record Items(boolean enabled, Set<String> disabledWorlds, Duration minimumAge, Mode mode,
                        Set<Material> materials, ItemFlags flags, Map<Material, ItemFlags> specific) {
    }

    public record Entities(boolean enabled, Set<String> disabledWorlds, Mode mode, Set<EntityType> types,
                           EntityFlags flags, Map<EntityType, EntityFlags> specific) {
    }

    public static Settings load(FileConfiguration cfg, Logger log) {
        final Loader loader = new Loader(cfg, log);
        return new Settings(loader.general(), loader.items(), loader.entities());
    }

    private static final class Loader {
        private final FileConfiguration cfg;
        private final Logger log;

        Loader(FileConfiguration cfg, Logger log) {
            this.cfg = cfg;
            this.log = log;
        }

        General general() {
            final Duration interval = duration("interval", Duration.ofMinutes(10));
            final List<Long> warnings = new ArrayList<>();
            for (String raw : cfg.getStringList("warning_times")) {
                final long seconds = parse("warning_times", raw, Duration.ZERO).toSeconds();
                if (seconds > 0 && seconds < interval.toSeconds()) {
                    warnings.add(seconds);
                }
            }
            warnings.sort(Comparator.reverseOrder());

            final long budgetMs = Math.max(1, cfg.getLong("performance.max_ms_per_tick", 3));
            final DropGuard guard = new DropGuard(
                    cfg.getBoolean("drop_prevention.enabled", true),
                    duration("drop_prevention.duration", Duration.ofSeconds(30)));
            final TpsTrigger tps = new TpsTrigger(
                    cfg.getBoolean("tps_trigger.enabled", false),
                    cfg.getDouble("tps_trigger.below", 16.0),
                    duration("tps_trigger.check_every", Duration.ofSeconds(10)),
                    duration("tps_trigger.cooldown", Duration.ofMinutes(2)));
            return new General(interval, List.copyOf(warnings), budgetMs * 1_000_000L,
                    cfg.getBoolean("silent_when_empty", true), guard, tps);
        }

        Items items() {
            final ConfigurationSection protect = cfg.getConfigurationSection("items.protect");
            final ItemFlags base = new ItemFlags(
                    cfg.getBoolean("items.protect.named", true),
                    cfg.getBoolean("items.protect.metadata", true),
                    cfg.getBoolean("items.protect.owner", false));

            final Map<Material, ItemFlags> specific = new EnumMap<>(Material.class);
            final ConfigurationSection overrides = protect == null ? null : protect.getConfigurationSection("specific");
            if (overrides != null) {
                for (String key : overrides.getKeys(false)) {
                    final ConfigurationSection sub = overrides.getConfigurationSection(key);
                    if (sub == null) {
                        continue;
                    }
                    final ItemFlags flags = new ItemFlags(
                            sub.getBoolean("named", base.named()),
                            sub.getBoolean("metadata", base.metadata()),
                            sub.getBoolean("owner", base.owner()));
                    for (Material material : materials(key)) {
                        specific.put(material, flags);
                    }
                }
            }

            final Set<Material> listed = EnumSet.noneOf(Material.class);
            for (String entry : cfg.getStringList("items.materials.list")) {
                listed.addAll(materials(entry));
            }
            return new Items(
                    cfg.getBoolean("items.enabled", true),
                    worlds("items.disabled_worlds"),
                    duration("items.minimum_age", Duration.ofSeconds(10)),
                    mode("items.materials.mode", Mode.BLACKLIST),
                    listed, base, specific);
        }

        Entities entities() {
            final EntityFlags base = new EntityFlags(
                    cfg.getBoolean("entities.protect.named", true),
                    cfg.getBoolean("entities.protect.metadata", true),
                    cfg.getBoolean("entities.protect.team", true),
                    cfg.getBoolean("entities.protect.leashed", true),
                    cfg.getBoolean("entities.protect.tamed", true),
                    cfg.getBoolean("entities.protect.bred", true),
                    cfg.getBoolean("entities.protect.equipped", false),
                    cfg.getBoolean("entities.protect.vehicles", true));

            final Map<EntityType, EntityFlags> specific = new EnumMap<>(EntityType.class);
            final ConfigurationSection overrides = cfg.getConfigurationSection("entities.protect.specific");
            if (overrides != null) {
                for (String key : overrides.getKeys(false)) {
                    final ConfigurationSection sub = overrides.getConfigurationSection(key);
                    final EntityType type = entityType(key);
                    if (sub == null || type == null) {
                        continue;
                    }
                    specific.put(type, new EntityFlags(
                            sub.getBoolean("named", base.named()),
                            sub.getBoolean("metadata", base.metadata()),
                            sub.getBoolean("team", base.team()),
                            sub.getBoolean("leashed", base.leashed()),
                            sub.getBoolean("tamed", base.tamed()),
                            sub.getBoolean("bred", base.bred()),
                            sub.getBoolean("equipped", base.equipped()),
                            sub.getBoolean("vehicles", base.vehicles())));
                }
            }

            final Set<EntityType> listed = EnumSet.noneOf(EntityType.class);
            for (String entry : cfg.getStringList("entities.types.list")) {
                final EntityType type = entityType(entry);
                if (type != null) {
                    listed.add(type);
                }
            }
            return new Entities(
                    cfg.getBoolean("entities.enabled", false),
                    worlds("entities.disabled_worlds"),
                    mode("entities.types.mode", Mode.WHITELIST),
                    listed, base, specific);
        }

        private Set<String> worlds(String path) {
            final Set<String> names = new HashSet<>();
            for (String name : cfg.getStringList(path)) {
                names.add(name.toLowerCase(Locale.ROOT));
            }
            return Set.copyOf(names);
        }

        private Mode mode(String path, Mode fallback) {
            final String raw = cfg.getString(path, fallback.name());
            try {
                return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                log.warning(path + " must be whitelist or blacklist, using " + fallback.name().toLowerCase(Locale.ROOT));
                return fallback;
            }
        }

        private Duration duration(String path, Duration fallback) {
            return parse(path, cfg.getString(path, ""), fallback);
        }

        private Duration parse(String path, String raw, Duration fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            try {
                return Durations.parse(raw);
            } catch (IllegalArgumentException e) {
                log.warning(path + ": '" + raw + "' is not a duration, using " + fallback.toSeconds() + "s");
                return fallback;
            }
        }

        /** A material name, or an item tag written as #minecraft:swords. */
        private Set<Material> materials(String entry) {
            final String name = entry.trim();
            if (name.startsWith("#")) {
                final NamespacedKey key = NamespacedKey.fromString(name.substring(1).toLowerCase(Locale.ROOT));
                final Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
                if (tag == null) {
                    log.warning("Unknown item tag " + name + ", skipping it");
                    return Set.of();
                }
                return tag.getValues();
            }
            final Material material = Material.matchMaterial(name);
            if (material == null) {
                log.warning("Unknown material " + name + ", skipping it");
                return Set.of();
            }
            return Set.of(material);
        }

        private EntityType entityType(String name) {
            final String lower = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            final NamespacedKey key = NamespacedKey.fromString(lower);
            final EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
            if (type == null) {
                log.warning("Unknown entity type " + name + ", skipping it");
            }
            return type;
        }
    }
}
