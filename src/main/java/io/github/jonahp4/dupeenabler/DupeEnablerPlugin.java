package io.github.jonahp4.dupeenabler;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

public final class DupeEnablerPlugin extends JavaPlugin {

    static final int DEFAULT_WATCHDOG_INTERVAL = 20;

    /** Dense array instead of a map so the watchdog loop allocates nothing. */
    private ManagedSwitch[] switches = new ManagedSwitch[0];
    private final @Nullable ManagedSwitch[] byDupe = new ManagedSwitch[Dupe.values().length];
    private @Nullable PaperSwitch tripwireUpdatesDisabled;
    private @Nullable BukkitTask watchdog;
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
            this.getSLF4JLogger().error("None of Paper's dupe settings exist on this server (is it running Paper 1.21.x?). Disabling.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.tripwireUpdatesDisabled = optionalSwitch("blockUpdates", "disableTripwireUpdates");

        this.applyConfig();
        this.registerCommand("dupes", "Toggle piston (TNT/carpet/rail), tripwire hook and gravity block duping",
            List.of("dupeenabler"), new DupesCommand(this));
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

    /** Human-readable reasons why an enabled dupe may still not work; empty if none. */
    List<String> problems(ManagedSwitch managed) {
        if (managed.dupe() == Dupe.TRIPWIRE && this.tripwireUpdatesDisabled != null && this.tripwireUpdatesDisabled.get()) {
            return List.of("block-updates.disable-tripwire-updates is true in paper-global.yml, so tripwires never update and hook dupers cannot work");
        }
        return List.of();
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
            for (String problem : this.problems(managed)) {
                this.getSLF4JLogger().warn("{} duping is enabled but {}", capitalize(managed.dupe().id()), problem);
            }
        }
    }

    /**
     * {@code /paper reload} and {@code /reload} make Paper re-read {@code paper-global.yml} into the
     * live config, which would silently undo our values. The watchdog puts them back: a handful of
     * field reads every interval, no events, no allocation.
     */
    private void startWatchdog(int intervalTicks) {
        this.stopWatchdog();
        if (intervalTicks <= 0) {
            return;
        }
        this.watchdog = this.getServer().getScheduler().runTaskTimer(this, this::enforceAll, intervalTicks, intervalTicks);
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
                    this.getSLF4JLogger().info("Paper reset {} (config reload?); re-applied {}",
                        managed.dupe().paperSetting(), managed.desired());
                }
            } catch (RuntimeException e) {
                this.getSLF4JLogger().error("Could not enforce {}; stopping the watchdog", managed.dupe().paperSetting(), e);
                this.stopWatchdog();
                return;
            }
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
