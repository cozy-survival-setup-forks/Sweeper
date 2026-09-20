package dev.sweeper.sweep;

import dev.sweeper.config.Settings;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * Decides whether a dropped item may be removed. The cheap checks come first so most items are
 * settled without ever looking at their stack.
 */
final class ItemRules {

    private final Settings.Items settings;
    private final int minimumAgeTicks;

    ItemRules(Settings.Items settings) {
        this.settings = settings;
        this.minimumAgeTicks = (int) Math.min(Integer.MAX_VALUE, settings.minimumAge().toMillis() / 50);
    }

    boolean shouldRemove(Item item) {
        if (item.getTicksLived() < minimumAgeTicks) {
            return false;
        }

        if (item.getPersistentDataContainer().has(dev.sweeper.guard.DeathDrops.KEEP)) {
            return false;
        }

        final ItemStack stack = item.getItemStack();
        final Material type = stack.getType();
        if (!removableType(type)) {
            return false;
        }

        final Settings.ItemFlags flags = settings.specific().getOrDefault(type, settings.flags());
        if (flags.owner() && (item.getOwner() != null || item.getThrower() != null)) {
            return false;
        }
        if (flags.named() && stack.hasData(DataComponentTypes.CUSTOM_NAME)) {
            return false;
        }
        if (flags.metadata()) {
            if (!item.getScoreboardTags().isEmpty() || !item.getPersistentDataContainer().isEmpty()) {
                return false;
            }
            return stack.getPersistentDataContainer().isEmpty();
        }
        return true;
    }

    private boolean removableType(Material type) {
        final boolean listed = settings.materials().contains(type);
        return settings.mode() == Settings.Mode.WHITELIST ? listed : !listed;
    }
}
