# Sweeper

Timed clean-ups for dropped items and mobs on Paper 1.21.11. It warns players first, removes
things in small slices so the tick stays smooth, and leaves anything you have marked as worth
keeping.

## Features

- Automatic clean-up on a timer, with warnings at the times you choose
- Work is spread over several ticks with a per-tick time limit, so thousands of drops do not
  cause a freeze
- Items: keep or remove by material or item tag, a minimum age, and protection for renamed items,
  items with tags, custom data, enchantments, lore, book text or contents (bundles, containers), and
  items that remember who dropped them. Death drops are always kept
- Mobs: keep or remove by type, and protection for named, tagged, team, leashed, tamed, baby or recently
  bred, equipped (including saddles) or ridden mobs. A mob that picked up an item, like a sword from a
  player it killed, is never removed
- Separate world lists for items and mobs
- Drop protection: what a player drops shortly before a clean-up is kept out of it, so nothing they
  just dropped disappears a second later. Drops are never blocked. Each player can switch it off for themselves
- Every message can be chat, action bar or both, with an optional sound
- Optional extra clean-up when the TPS drops
- PlaceholderAPI support

## Commands

| Command | What it does | Permission |
| --- | --- | --- |
| `/sweeper timer` | Time until the next clean-up | `sweeper.command.timer` |
| `/sweeper messages` | Switch clean-up messages on or off for yourself | `sweeper.command.messages` |
| `/sweeper protection` | Switch your drop protection on or off | `sweeper.command.protection` |
| `/sweeper clean [items\|entities]` | Run a clean-up now | `sweeper.admin` |
| `/sweeper reload` | Reload `config.yml` and `lang.yml`; a file that cannot be read keeps the old settings | `sweeper.admin` |

`/sweep` works as well. `sweeper.bypass.dropguard` makes someone's drops count as normal ones during the
protected window; nobody has it by default, not even operators.

`/sweeper clean` and the TPS trigger start at once, with no warning. Items younger than `minimum_age` and
items dropped in the protected window are still left alone.

## Placeholders

- `%sweeper_time%` - time until the next clean-up, like `4m 30s`
- `%sweeper_seconds%` - the same in seconds
- `%sweeper_last_removed%` - how many things the last clean-up removed

## Building

Needs Java 21.

```
./gradlew build
```

The jar ends up in `build/libs`. To try it on a local server:

```
./gradlew runServer
```

## Configuration

`config.yml` holds the timings and rules, `lang.yml` holds every message. Both are short and
commented. Durations are written like `30s`, `10m` or `1h30m`.

## Telemetry

On startup Sweeper sends a small anonymous beacon (plugin name/version, server software/version,
online/max player counts, and a random ID with no player data) so we know which versions are in
use. Turn it off with `metrics.enabled: false` in `config.yml`.

## License

See `LICENSE`: free to run on your own servers, not for redistribution or resale.
