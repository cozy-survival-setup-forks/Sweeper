package dev.sweeper.sweep;

import dev.sweeper.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Decides whether a mob may be removed. Type is checked first, the protection flags only run
 * for mobs that would otherwise go.
 */
final class EntityRules {

    private static final EquipmentSlot[] WORN = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.BODY, EquipmentSlot.SADDLE
    };

    private final Settings.Entities settings;
    private final Scoreboard scoreboard;

    EntityRules(Settings.Entities settings) {
        this.settings = settings;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    boolean shouldRemove(Mob mob) {
        final EntityType type = mob.getType();
        final boolean listed = settings.types().contains(type);
        if (settings.mode() == Settings.Mode.WHITELIST ? !listed : listed) {
            return false;
        }

        final Settings.EntityFlags flags = settings.specific().getOrDefault(type, settings.flags());
        if (flags.named() && mob.customName() != null) {
            return false;
        }
        if (flags.metadata() && (!mob.getScoreboardTags().isEmpty() || !mob.getPersistentDataContainer().isEmpty())) {
            return false;
        }
        if (flags.team() && scoreboard.getEntityTeam(mob) != null) {
            return false;
        }
        if (flags.leashed() && mob.isLeashed()) {
            return false;
        }
        if (flags.tamed() && mob instanceof Tameable pet && pet.isTamed()) {
            return false;
        }
        if (flags.bred() && mob instanceof Breedable animal && (!animal.isAdult() || animal.getAge() > 0)) {
            return false;
        }
        if (flags.vehicles() && hasPlayerPassenger(mob)) {
            return false;
        }
        if (carriesPickedUpGear(mob)) {
            return false;
        }
        return !(flags.equipped() && isEquipped(mob));
    }

    /**
     * A mob that picked something up (say a sword from a player it killed) keeps it with a guaranteed drop
     * chance. Removing that mob would delete the item, so it is never removed, whatever the flags say.
     */
    private static boolean carriesPickedUpGear(Mob mob) {
        final EntityEquipment equipment = mob.getEquipment();
        for (EquipmentSlot slot : WORN) {
            if (mob.canUseEquipmentSlot(slot) && !equipment.getItem(slot).isEmpty() && equipment.getDropChance(slot) > 1f) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPlayerPassenger(Mob mob) {
        for (var passenger : mob.getPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEquipped(Mob mob) {
        final EntityEquipment equipment = mob.getEquipment();
        for (EquipmentSlot slot : WORN) {
            if (mob.canUseEquipmentSlot(slot) && !equipment.getItem(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
