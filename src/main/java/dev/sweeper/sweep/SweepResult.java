package dev.sweeper.sweep;

/**
 * What a finished clean-up removed.
 */
public record SweepResult(int items, int entities, long ticks) {

    public int total() {
        return items + entities;
    }
}
