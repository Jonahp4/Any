package io.github.jonahp4.dupeenabler;

import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public final class DupeEnablerPlugin extends JavaPlugin {

    static final int DEFAULT_WATCHDOG_INTERVAL = 1;

    /** Dense array instead of a map so the watchdog loop allocates nothing. */
    private ManagedSwitch[] switches = new ManagedSwitch[0];
    private final @Nullable ManagedSwitch[] byDupe = new ManagedSwitch[Dupe.values().length];
    private @Nullable PaperSwitch tripwireUpdatesDisabled;
    private @Nullable PaperSwitch noteblockUpdatesDisabled;
    private @Nullable ScheduledTask watchdog;
    private boolean restoreOnDisable = true;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        List<ManagedSwitch> found = new ArrayList<>();
        for (Dupe dupe : Dupe.values()) {
            try {
                PaperSwitch handle = PaperSwitch.resolve(dupe.section(), dupe.field());
                handle.get(); // fail now rather than in the watchdog
                ManagedSwitch managed = new ManagedSwitch(dupe, handle);
                found.add(managed);
                this.byDupe[dupe.ordinal()] = managed;
            } catch (ReflectiveOperationException | RuntimeException e) {
                this.getSLF4JLogger().warn("{} duping is not supported by this server: Paper setting '{}' not found ({})",
                    dupe.id(), dupe.paperSetting(), e.toString());
            }
        }
        this.switches = found.toArray(ManagedSwitch[]::new);

        if (this.switches.length == 0) {
            this.getSLF4JLogger().error("None of Paper's dupe settings exist on this server (is it running Paper 1.21.11?). Disabling.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.tripwireUpdatesDisabled = optionalSwitch("blockUpdates", "disableTripwireUpdates");
        this.noteblockUpdatesDisabled = optionalSwitch("blockUpdates", "disableNoteblockUpdates");

        this.applyConfig();
        try {
            this.registerCommand("dupes", "Toggle piston (TNT/carpet/rail), tripwire hook and gravity block duping",
                List.of("dupeenabler"), new DupesCommand(this));
        } catch (IllegalStateException e) {
            // Paper only allows this once per plugin instance; a plugin manager re-enabling us hits that.
            this.getSLF4JLogger().warn("Could not register /dupes ({}); restart the server to get the command back", e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        this.stopWatchdog();
        if (this.restoreOnDisable) {
            for (ManagedSwitch managed : this.switches) {
                try {
                    managed.restore();
                } catch (RuntimeException e) {
                    this.getSLF4JLogger().warn("Could not restore Paper's {}", managed.dupe().paperSetting(), e);
                }
            }
        }
    }

    /** Re-reads {@code config.yml} and applies it. */
    void reload() {
        this.reloadConfig();
        this.applyConfig();
    }

    @Nullable ManagedSwitch managed(Dupe dupe) {
        return this.byDupe[dupe.ordinal()];
    }

    /** Changes one dupe, persists it to {@code config.yml} and applies it immediately. */
    void setEnabled(ManagedSwitch managed, boolean enabled) {
        managed.desired(enabled);
        this.getConfig().set(managed.dupe().configPath(), enabled);
        this.saveConfig();
        managed.enforce();
        this.warnIfIneffective(managed);
    }

    /** Server settings that stop an enabled dupe from working; empty if none were found. */
    List<String> problems(Dupe dupe) {
        List<String> problems = new ArrayList<>();
        switch (dupe) {
            case PISTON -> {
                List<String> noTnt = new ArrayList<>();
                for (World world : this.getServer().getWorlds()) {
                    if (Boolean.FALSE.equals(world.getGameRuleValue(GameRules.TNT_EXPLODES))) {
                        noTnt.add(world.getName());
                    }
                }
                if (!noTnt.isEmpty()) {
                    problems.add("gamerule tnt_explodes is false in " + String.join(", ", noTnt) + ", so TNT cannot be primed or duped there");
                }
                if (isOn(this.noteblockUpdatesDisabled)) {
                    problems.add("block-updates.disable-noteblock-updates is true in paper-global.yml, so TNT dupers that use note blocks will not fire");
                }
            }
            case TRIPWIRE -> {
                if (isOn(this.tripwireUpdatesDisabled)) {
                    problems.add("block-updates.disable-tripwire-updates is true in paper-global.yml, so tripwires never update and hook dupers cannot work");
                }
            }
            case GRAVITY -> {
            }
        }
        return problems;
    }

    /**
     * Other plugins with a non-MONITOR listener on an event that, if cancelled, stops this dupe.
     * Only a hint (most listeners never cancel), and only computed on demand for {@code /dupes}.
     */
    Set<String> possibleBlockers(Dupe dupe) {
        HandlerList[] lists = switch (dupe) {
            case PISTON -> new HandlerList[] {
                BlockPistonExtendEvent.getHandlerList(), BlockPistonRetractEvent.getHandlerList(),
                TNTPrimeEvent.getHandlerList(), BlockPhysicsEvent.getHandlerList()
            };
            case TRIPWIRE -> new HandlerList[] {
                BlockRedstoneEvent.getHandlerList(), EntityInsideBlockEvent.getHandlerList(), BlockPhysicsEvent.getHandlerList()
            };
            case GRAVITY -> new HandlerList[] {EntityPortalEvent.getHandlerList()};
        };
        Set<String> plugins = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (HandlerList list : lists) {
            for (RegisteredListener listener : list.getRegisteredListeners()) {
                if (listener.getPriority() != EventPriority.MONITOR && listener.getPlugin() != this) {
                    plugins.add(listener.getPlugin().getName());
                }
            }
        }
        return plugins;
    }

    private void applyConfig() {
        this.restoreOnDisable = this.getConfig().getBoolean("restore-on-disable", true);
        for (ManagedSwitch managed : this.switches) {
            Dupe dupe = managed.dupe();
            managed.desired(this.getConfig().getBoolean(dupe.configPath(), dupe.enabledByDefault()));
            managed.enforce();
            this.getSLF4JLogger().info("{} duping ({}): {}", capitalize(dupe.id()), dupe.description(),
                managed.desired() ? "ENABLED" : "disabled");
            this.warnIfIneffective(managed);
        }
        this.startWatchdog(this.getConfig().getInt("watchdog-interval-ticks", DEFAULT_WATCHDOG_INTERVAL));
    }

    private void warnIfIneffective(ManagedSwitch managed) {
        if (managed.desired()) {
            for (String problem : this.problems(managed.dupe())) {
                this.getSLF4JLogger().warn("{} duping is enabled but {}", capitalize(managed.dupe().id()), problem);
            }
        }
    }

    /**
     * {@code /paper reload} and {@code /reload} make Paper load {@code paper-global.yml} again, which
     * would silently undo our values. The watchdog puts them back. It runs on Paper's global region
     * scheduler, which ticks before any world does, so with the default interval of one tick no
     * piston or hook update ever sees Paper's value. Each run is a few handle calls and field reads,
     * with no events and no allocation.
     */
    private void startWatchdog(int intervalTicks) {
        this.stopWatchdog();
        if (intervalTicks <= 0) {
            return;
        }
        this.watchdog = this.getServer().getGlobalRegionScheduler()
            .runAtFixedRate(this, task -> this.enforceAll(), intervalTicks, intervalTicks);
    }

    private void stopWatchdog() {
        if (this.watchdog != null) {
            this.watchdog.cancel();
            this.watchdog = null;
        }
    }

    private void enforceAll() {
        for (ManagedSwitch managed : this.switches) {
            try {
                if (managed.enforce()) {
                    this.getSLF4JLogger().info("Paper reloaded its config and reset {}; re-applied {}",
                        managed.dupe().paperSetting(), managed.desired());
                }
            } catch (RuntimeException e) {
                this.getSLF4JLogger().error("Could not enforce {}; stopping the watchdog", managed.dupe().paperSetting(), e);
                this.stopWatchdog();
                return;
            }
        }
    }

    private static boolean isOn(@Nullable PaperSwitch handle) {
        try {
            return handle != null && handle.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static @Nullable PaperSwitch optionalSwitch(String section, String field) {
        try {
            PaperSwitch handle = PaperSwitch.resolve(section, field);
            handle.get();
            return handle;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
