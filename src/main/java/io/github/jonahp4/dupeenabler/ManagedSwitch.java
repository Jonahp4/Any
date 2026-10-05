package io.github.jonahp4.dupeenabler;

import org.jspecify.annotations.Nullable;

/**
 * Keeps one {@link PaperSwitch} at the value the plugin wants, while remembering Paper's own value
 * (from {@code paper-global.yml}) so it can be handed back when the plugin is disabled.
 *
 * <p>Reloads are detected by identity: {@code /paper reload} and Bukkit's {@code /reload} replace the
 * section object with one freshly loaded from disk, so a different object means "Paper's value
 * again", whatever it happens to be.
 *
 * <p>Not thread-safe; only touched from the server thread.
 */
final class ManagedSwitch {

    private final Dupe dupe;
    private final PaperSwitch handle;

    private boolean desired;
    /** The section object we last wrote to, or {@code null} while not managing the switch. */
    private @Nullable Object managedSection;
    /** Paper's value on {@link #managedSection} before we wrote to it. */
    private boolean paperValue;

    ManagedSwitch(Dupe dupe, PaperSwitch handle) {
        this.dupe = dupe;
        this.handle = handle;
    }

    Dupe dupe() {
        return this.dupe;
    }

    boolean desired() {
        return this.desired;
    }

    void desired(boolean desired) {
        this.desired = desired;
    }

    /** The value Paper is actually using right now. */
    boolean live() {
        return this.handle.get();
    }

    /** Paper's own value for this setting, i.e. what {@code paper-global.yml} says. */
    boolean paperValue() {
        return this.managedSection != null ? this.paperValue : this.handle.get();
    }

    /**
     * Makes Paper use the desired value. Cheap enough to call every tick: two method handle calls,
     * an identity check and a field read; it only writes when the value is wrong.
     *
     * @return {@code true} if Paper reloaded its config since the last call and the reload had
     *     switched the value away from the desired one (it has been put back)
     */
    boolean enforce() {
        Object section = this.handle.section();
        boolean reloaded = false;
        if (section != this.managedSection) {
            // First call, or Paper loaded a new section object from disk: its value is Paper's own.
            reloaded = this.managedSection != null;
            this.managedSection = section;
            this.paperValue = this.handle.get(section);
        }
        if (this.handle.get(section) != this.desired) {
            this.handle.set(section, this.desired);
            return reloaded;
        }
        return false;
    }

    /**
     * Hands the setting back to Paper. Does nothing if Paper reloaded since our last write (as Bukkit's
     * {@code /reload} does just before disabling plugins) or if something else changed the value.
     */
    void restore() {
        Object section = this.managedSection;
        this.managedSection = null;
        if (section != null && this.handle.section() == section && this.handle.get(section) == this.desired) {
            this.handle.set(section, this.paperValue);
        }
    }
}
