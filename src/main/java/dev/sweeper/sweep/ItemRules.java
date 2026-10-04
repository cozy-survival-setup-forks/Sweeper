package dev.sweeper.sweep;

import dev.sweeper.config.Settings;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.datacomponent.item.ItemLore;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Decides whether a dropped item may be removed. The cheap checks come first so most items are
 * settled without ever looking at their stack.
 */
final class ItemRules {

    private final Settings.Items settings;
    private final int minimumAgeTicks;
    private final int cycle;

    /** {@code cycle} is the number of this clean-up; items kept for it by the drop guard are skipped. */
    ItemRules(Settings.Items settings, int cycle) {
        this.settings = settings;
        this.cycle = cycle;
        this.minimumAgeTicks = (int) Math.min(Integer.MAX_VALUE, Math.min(settings.minimumAge().toSeconds(), 100_000_000L) * 20);
    }

    boolean shouldRemove(Item item) {
        if (item.getTicksLived() < minimumAgeTicks) {
            return false;
        }

        final var marks = item.getPersistentDataContainer();
        if (marks.has(dev.sweeper.guard.DeathDrops.KEEP)) {
            return false;
        }
        final Integer guarded = marks.get(dev.sweeper.guard.DropGuard.KEEP_FOR, PersistentDataType.INTEGER);
        if (guarded != null && guarded >= cycle) {
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
            return stack.getPersistentDataContainer().isEmpty() && !hasValuableData(stack);
        }
        return true;
    }

    /** Enchantments, lore, stored contents and written books count as data worth keeping. */
    private static boolean hasValuableData(ItemStack stack) {
        final ItemEnchantments enchants = stack.getData(DataComponentTypes.ENCHANTMENTS);
        if (enchants != null && !enchants.enchantments().isEmpty()) {
            return true;
        }
        final ItemEnchantments stored = stack.getData(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored != null && !stored.enchantments().isEmpty()) {
            return true;
        }
        final ItemLore lore = stack.getData(DataComponentTypes.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            return true;
        }
        final BundleContents bundle = stack.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.contents().isEmpty()) {
            return true;
        }
        final ItemContainerContents container = stack.getData(DataComponentTypes.CONTAINER);
        if (container != null && !container.contents().isEmpty()) {
            return true;
        }
        return stack.hasData(DataComponentTypes.WRITTEN_BOOK_CONTENT);
    }

    private boolean removableType(Material type) {
        final boolean listed = settings.materials().contains(type);
        return settings.mode() == Settings.Mode.WHITELIST ? listed : !listed;
    }
}
