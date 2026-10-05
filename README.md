# DupeEnabler

A tiny Paper **1.21.11** plugin that turns vanilla duping back on:

| Dupe | Default | What works again |
|---|---|---|
| `piston` | **on** | TNT duping, carpet duping, rail duping |
| `tripwire` | **on** | Tripwire hook duping |
| `gravity` | off | Sand / gravel / concrete powder duping through end portals |

Toggle each one live with `/dupes`. No restart needed.

## How it works (and why it costs nothing)

Paper patches these dupes out, but it keeps the original vanilla code behind its own switches in
`paper-global.yml → unsupported-settings`, and reads those switches straight from memory every time
the code runs. DupeEnabler flips those switches at runtime:

| Dupe | Paper switch | Only place Paper reads it |
|---|---|---|
| `piston` | `allow-piston-duplication` | `PistonBaseBlock#moveBlocks` (+ a client-sync flag in `PistonMovingBlockEntity`) |
| `tripwire` | `skip-tripwire-hook-placement-validation` | `TripWireHookBlock#calculateState` |
| `gravity` | `allow-unsafe-end-portal-teleportation` | `FallingBlockEntity#teleport` |

These are the only reads of each switch in the Paper 1.21.11 source, so each one turns on that one
mechanic and nothing else. None of them enables bedrock breaking, headless pistons or update
suppression. With a switch on, Paper runs **the same code as vanilla** for that mechanic.

That means:

- **No event listeners** and no NMS or mixins. The plugin never runs on piston, redstone or entity
  code paths.
- **Next to no per-tick work.** The only thing that runs is a watchdog: a couple of method-handle
  calls and field reads per tick (tens of nanoseconds), and it allocates nothing.
- **Your `paper-global.yml` is never edited.** Remove the plugin and Paper goes back to its own values.

### `/paper reload` and `/reload`

Both make Paper load `paper-global.yml` again, into brand-new config objects, which would switch
the dupes off without telling you. The watchdog sees the new object and puts the values back. It
runs on Paper's global scheduler, which ticks before any world does, so with the default
`watchdog-interval-ticks: 1` no piston or hook update ever runs with Paper's value. That matters
because a TNT duper that fires with the fix active uses up its TNT block.

On disable the plugin hands Paper its own values back. If Paper reloaded its config in the meantime
(Bukkit's `/reload` does that just before disabling plugins), the freshly loaded values are left
alone.

## About string duping

Mojang fixed **string** duping in vanilla itself in 1.21.2 (snapshot 24w33a). Vanilla 1.21.11's
own tripwire code contains that fix, so no Paper setting or plugin can bring it back without
changing how the game works. What 1.21.11 still has is **tripwire hook** duping, and that is what
`tripwire` turns on.

### Which hook dupers work

- **Triggering-type** dupers work. In these, hooks sit on powered/open doors or trapdoors, the
  tripwire changes state (an entity, water, etc.), the passive hook's door update pops the active
  hook, and vanilla puts it back. The dupe usually fires when the tripwire **turns off**.
- **Placing-type** dupers, where you dupe by *placing* a hook to complete the circuit, **cannot
  work on Paper**, whatever the settings. Paper holds back every block update caused by a player
  placing a block until after `BlockPlaceEvent`, so the chain that would pop the hook runs too late.
- Some designs (for example iron-door variants) are reported not to dupe on Paper even with the
  switch on (Paper issues #12114 and #12159). The cause is inside Paper, not this plugin.

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
| `/dupes` | Shows each dupe, settings that would stop it working, and plugins that listen to events that could block it |
| `/dupes <piston\|tripwire\|gravity>` | Toggles one dupe |
| `/dupes <piston\|tripwire\|gravity\|all> <on\|off\|toggle>` | Sets one dupe, or all of them |
| `/dupes reload` | Re-reads `config.yml` |

Changes made with the command are saved to `config.yml`. Alias: `/dupeenabler`.

## Config (`plugins/DupeEnabler/config.yml`)

```yaml
dupes:
  piston: true     # TNT, carpet & rail duping
  tripwire: true   # tripwire hook duping
  gravity: false   # sand/gravel/concrete powder through end portals
watchdog-interval-ticks: 1    # re-apply after /paper reload; 0 = never re-check
restore-on-disable: true      # give Paper its own values back when the plugin is disabled
```

## If a duper still doesn't work

The plugin restores vanilla behaviour inside Paper. Other things can still get in the way, and
`/dupes` points out the ones it can detect:

- **Other plugins cancelling events.** Protection, anti-grief, anti-lag and entity-limiter plugins
  can cancel `BlockPistonExtendEvent`/`BlockPistonRetractEvent`, `TNTPrimeEvent`,
  `BlockPhysicsEvent`, `EntitySpawnEvent` (primed TNT), `BlockRedstoneEvent`,
  `EntityInsideBlockEvent`, or `PHYSICAL` interactions such as stepping on string. If one of these
  is cancelled, the duper stops. In WorldGuard, check region flags such as `pistons` and `tnt`.
- **Gamerule `tnt_explodes false`.** TNT can't be primed at all, so there is nothing to dupe.
  *(detected)*
- **`paper-global.yml → block-updates.disable-tripwire-updates: true`** turns off tripwire logic
  completely, and **`disable-noteblock-updates: true`** breaks TNT dupers that use note blocks.
  Custom-block plugins (Oraxen, ItemsAdder, Nexo) often ask you to turn these on. The plugin warns
  about them but doesn't change them. *(detected)*
- **`paper-world-defaults.yml`.** `misc.redstone-implementation` should be `VANILLA` (the default),
  because `ALTERNATE_CURRENT` and `EIGENCRAFT` change update order. `fixes.prevent-tnt-from-moving-in-water`
  should be `false` (the default); when `true`, water no longer pushes primed TNT.
- **`spigot.yml → max-tnt-per-tick`** (default 100 per world). Only matters for very large TNT
  machines, where extra primed TNT is frozen for that tick.
- **`allow-headless-pistons: false`** (Paper's default). When an explosion breaks a moving piston,
  Paper can also break the piston base, or the block behind a retracting one (read from the code, not tested). This can affect TNT
  world eaters or tunnel bores whose pistons get caught in a blast.

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
- Carpet and rail duping come bundled with TNT duping (Paper has a single switch for all three).
  These are real item dupes, so think about it on economy servers.
- These are Paper "unsupported settings". Paper may not accept bug reports from servers that have
  them on.
- If you delete `paper-global.yml` (or its `unsupported-settings` section) and run `/paper reload`
  while the plugin is enabled, Paper rewrites the file from the values in memory, which are the
  plugin's. Restart the server instead of reloading after deleting Paper's config.
- The plugin uses Paper internals (`io.papermc.paper.configuration.GlobalConfiguration`) through
  reflection. If a future Paper build renames a switch, that dupe is reported as "not supported"
  and everything else keeps working.
