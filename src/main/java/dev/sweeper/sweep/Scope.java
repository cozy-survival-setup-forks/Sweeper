package dev.sweeper.sweep;

/**
 * Which kinds of things a clean-up looks at.
 */
public enum Scope {
    ITEMS,
    ENTITIES,
    ALL;

    boolean includesItems() {
        return this != ENTITIES;
    }

    boolean includesEntities() {
        return this != ITEMS;
    }
}
