# Sweeper

Timed clean-ups for dropped items and mobs on Paper 1.21.11. It warns players first, removes
things in small slices so the tick stays smooth, and leaves anything you have marked as worth
keeping.

## Features

- Automatic clean-up on a timer, with warnings at the times you choose
- Work is spread over several ticks with a per-tick time limit, so thousands of drops do not
  cause a freeze
- Items: keep or remove by material or item tag, a minimum age, and protection for renamed items,
  items with tags or data, and items that remember who dropped them
- Mobs: keep or remove by type, and protection for named, tagged, team, leashed, tamed, recently
  bred, equipped or ridden mobs
- Separate world lists for items and mobs
- Drop protection just before a clean-up, which each player can switch off for themselves
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
| `/sweeper reload` | Reload `config.yml` and `lang.yml` | `sweeper.admin` |

`/sweep` works as well. `sweeper.bypass.dropguard` lets someone drop items during the
protected window.

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
