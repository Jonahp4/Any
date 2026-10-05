package io.github.jonahp4.dupeenabler;

/**
 * Keeps one {@link PaperSwitch} at the value the plugin wants, while remembering Paper's own value
 * (from {@code paper-global.yml}) so it can be put back when the plugin is disabled.
 *
 * <p>Not thread-safe; only touched from the server thread.
 */
final class ManagedSwitch {

    private final Dupe dupe;
    private final PaperSwitch handle;

    private boolean desired;
    private boolean paperValue;   // what Paper had before we wrote, i.e. its config file value
    private boolean written;      // have we written at least once?
    private boolean lastWritten;

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

    /** Paper's own value for this setting, as last seen before the plugin overrode it. */
    boolean paperValue() {
        return this.written ? this.paperValue : this.handle.get();
    }

    /**
     * Makes Paper use the desired value.
     *
     * @return {@code true} if something other than this plugin had changed the value since our last
     *     write (e.g. {@code /paper reload} re-read {@code paper-global.yml}) and it was put back
     */
    boolean enforce() {
        boolean live = this.handle.get();
        boolean externallyChanged = this.written && live != this.lastWritten;
        if (!this.written || externallyChanged) {
            this.paperValue = live;
        }
        if (live != this.desired) {
            this.handle.set(this.desired);
        }
        this.written = true;
        this.lastWritten = this.desired;
        return externallyChanged && live != this.desired;
    }

    /** Hands the setting back to Paper, unless someone else changed it after our last write. */
    void restore() {
        if (this.written && this.handle.get() == this.lastWritten) {
            this.handle.set(this.paperValue);
        }
        this.written = false;
    }
}
