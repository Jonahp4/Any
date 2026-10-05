# DupeEnabler

A tiny Paper **1.21.11** plugin that turns vanilla duping back on:

| Dupe | Default | What works again |
|---|---|---|
| `piston` | **on** | TNT duping, carpet duping, rail duping (piston update desync) |
| `tripwire` | **on** | Tripwire hook duping |
| `gravity` | off | Sand / gravel / concrete powder duping through end portals |

Toggle each one live with `/dupes`. No restart needed.

## How it works (and why it costs nothing)

Paper patches these dupes out, but it keeps the original vanilla code behind its own switches in
`paper-global.yml → unsupported-settings`, and reads those switches straight from memory every time
the code runs. DupeEnabler flips those switches at runtime:

| Dupe | Paper switch | Where Paper reads it |
|---|---|---|
| `piston` | `allow-piston-duplication` | `PistonBaseBlock#moveBlocks`, `PistonMovingBlockEntity#tick` |
| `tripwire` | `skip-tripwire-hook-placement-validation` | `TripWireHookBlock#calculateState` |
| `gravity` | `allow-unsafe-end-portal-teleportation` | `FallingBlockEntity#teleport` |

Each switch is used only in the places listed (checked against the Paper 1.21.11 source), so it
turns on that one mechanic and nothing else. With a switch on, Paper runs the **same code as
vanilla**, so any design that works in singleplayer works here too.

That means:

- **No event listeners** and no NMS or mixins. The plugin never runs on piston, redstone or entity
  code paths.
- **No per-tick work**, apart from a watchdog that reads a few booleans once per second by default
  (see below). It allocates nothing.
- **Your `paper-global.yml` is never edited.** Remove the plugin and Paper goes back to its own values.

### `/paper reload` and `/reload`

Both make Paper re-read `paper-global.yml` into memory, which would switch the dupes off without
telling you. A watchdog (`watchdog-interval-ticks`, default 20 = 1 s) notices this and turns them
back on. It logs a line when that happens.

## About string duping

Mojang removed **string** duping from vanilla in **1.21.2** (snapshot 24w33a; it was MC-59471).
Vanilla 1.21.11 has the fix in its own tripwire code, so no Paper setting or plugin can bring it
back without changing how the game works. What 1.21.11 still has is **tripwire hook** duping, and
that is what `tripwire` turns on.

## Install

1. Get `DupeEnabler-1.0.0.jar`, either from the GitHub Actions run (**Actions → Build → artifact**)
   or by building it yourself (below).
2. Put it in `plugins/` and start the server. You should see:
   ```
   [DupeEnabler] Piston duping (TNT, carpet & rail duping with pistons): ENABLED
   [DupeEnabler] Tripwire duping (Tripwire hook duping): ENABLED
   [DupeEnabler] Gravity duping (Sand, gravel & concrete powder duping through end portals): disabled
   ```

Requires Paper 1.21.11 and Java 21.

## Commands

All commands need `dupeenabler.admin` (ops have it by default).

| Command | Does |
|---|---|
| `/dupes` | Shows the status of every dupe |
| `/dupes <piston\|tripwire\|gravity>` | Toggles one dupe |
| `/dupes <piston\|tripwire\|gravity\|all> <on\|off\|toggle>` | Sets one dupe, or all of them |
| `/dupes reload` | Re-reads `config.yml` |

Changes made with the command are saved to `config.yml`. Alias: `/dupeenabler`.

## Config (`plugins/DupeEnabler/config.yml`)

```yaml
dupes:
  piston: true     # TNT, carpet & rail duping
  tripwire: true   # tripwire hook duping
  gravity: false   # sand/gravel/concrete powder via end portals
watchdog-interval-ticks: 20   # re-apply after /paper reload; 0 = never re-check
restore-on-disable: true      # give Paper its own values back when the plugin is disabled
```

## If a duper still doesn't work

The plugin restores vanilla behaviour inside Paper, but some other things can still get in the way:

- **Protection and anti-grief plugins** (WorldGuard, GriefPrevention, CoreProtect rollbacks,
  anti-lag plugins) can cancel `BlockPistonExtendEvent`/`BlockPistonRetractEvent`,
  `TNTPrimeEvent`, `BlockPhysicsEvent`, explosion events, or `PHYSICAL` interactions such as
  stepping on string. If one of these is cancelled, the duper stops. Check region flags such as
  `pistons`, `tnt` and `other-explosion`.
- **`spigot.yml → max-tnt-per-tick`** (default 100 per world). Only matters for very large TNT
  machines, where extra primed TNT is frozen for a tick.
- **`paper-world-defaults.yml`**: `misc.redstone-implementation` should be `VANILLA` (the default),
  because `ALTERNATE_CURRENT` and `EIGENCRAFT` change update order. `fixes.prevent-tnt-from-moving-in-water`
  should be `false` (the default).
- **`paper-global.yml → block-updates.disable-tripwire-updates: true`** turns off tripwire logic
  completely, so hook dupers can't work. The plugin warns about this but does not change it,
  because servers with custom tripwire blocks rely on it.
- **Tripwire hook dupers:** a few designs (some iron-door variants) are reported not to dupe on
  Paper even with Paper's switch on (Paper issues #12114 and #12159). The cause is in Paper itself,
  not this plugin.
- **`allow-headless-pistons: false`** (Paper's default) means that when an explosion breaks a moving
  piston head, Paper breaks the piston base too. This can affect TNT machines whose pistons get
  caught in a blast.

Update-suppression tricks also behave differently on Paper (Spigot catches the `StackOverflowError`).
This plugin does not and cannot change that.

## Building

```bash
./gradlew build
# -> build/libs/DupeEnabler-1.0.0.jar
```

The jar's manifest marks it as Mojang-mapped (`paperweight-mappings-namespace: mojang`), so Paper
skips its plugin remapper on first start.

## Notes

- The settings are **global**: they apply to every world. Paper has no per-world version.
- These are Paper "unsupported settings". Paper may not accept bug reports from servers that have
  them on.
- The plugin uses Paper internals (`io.papermc.paper.configuration.GlobalConfiguration`) through
  reflection. If a future Paper build renames a switch, that dupe is reported as "not supported"
  and everything else keeps working.
