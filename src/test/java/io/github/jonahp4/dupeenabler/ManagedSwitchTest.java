package io.github.jonahp4.dupeenabler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.configuration.GlobalConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManagedSwitchTest {

    private GlobalConfiguration config;
    private ManagedSwitch piston;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        this.config = new GlobalConfiguration();
        GlobalConfiguration.set(this.config);
        this.piston = new ManagedSwitch(Dupe.PISTON, PaperSwitch.resolve("unsupportedSettings", "allowPistonDuplication"));
    }

    @AfterEach
    void tearDown() {
        GlobalConfiguration.set(null);
    }

    private boolean paper() {
        return GlobalConfiguration.get().unsupportedSettings.allowPistonDuplication;
    }

    /**
     * What /paper reload and Bukkit's /reload do in Paper 1.21.11: build a brand-new section object
     * from paper-global.yml and swap it into the existing root (InnerClassInstanceFactory#complete).
     */
    private void paperReload(boolean fileValue) {
        GlobalConfiguration.UnsupportedSettings fresh = this.config.new UnsupportedSettings();
        fresh.allowPistonDuplication = fileValue;
        this.config.unsupportedSettings = fresh;
    }

    @Test
    void enableRemembersPaperValue() {
        this.piston.desired(true);
        assertFalse(this.piston.enforce(), "first apply is not a reload");
        assertTrue(this.paper());
        assertFalse(this.piston.paperValue());

        this.piston.restore();
        assertFalse(this.paper());
    }

    @Test
    void reappliesAfterPaperReload() {
        this.piston.desired(true);
        this.piston.enforce();

        this.paperReload(false);
        assertFalse(this.paper());
        assertTrue(this.piston.enforce(), "reset by a reload must be reported");
        assertTrue(this.paper());
        assertFalse(this.piston.enforce(), "steady state is quiet");
        assertTrue(this.paper());
    }

    @Test
    void reloadThatAlreadyMatchesIsQuiet() {
        this.piston.desired(true);
        this.piston.enforce();

        this.paperReload(true); // admin also set it in paper-global.yml
        assertFalse(this.piston.enforce());
        assertTrue(this.paper());
        assertTrue(this.piston.paperValue());

        this.piston.restore();
        assertTrue(this.paper(), "restoring hands back the file value, which is true");
    }

    @Test
    void steadyStateDoesNotWrite() {
        this.piston.desired(true);
        this.piston.enforce();
        GlobalConfiguration.UnsupportedSettings section = this.config.unsupportedSettings;
        for (int i = 0; i < 1000; i++) {
            assertFalse(this.piston.enforce());
        }
        assertTrue(this.paper());
        assertTrue(section == this.config.unsupportedSettings);
    }

    @Test
    void foreignInPlaceWriteIsCorrectedWithoutForgettingPaperValue() {
        this.piston.desired(true);
        this.piston.enforce();

        this.config.unsupportedSettings.allowPistonDuplication = false; // some other plugin
        assertFalse(this.piston.enforce(), "not a reload");
        assertTrue(this.paper());
        assertFalse(this.piston.paperValue());
    }

    @Test
    void togglingOffDoesNotForgetPaperValue() {
        this.piston.desired(true);
        this.piston.enforce();

        this.piston.desired(false);
        assertFalse(this.piston.enforce(), "our own change is not a reload");
        assertFalse(this.paper());
        assertFalse(this.piston.paperValue(), "paper value must stay what paper-global.yml had");

        this.piston.desired(true);
        this.piston.enforce();
        this.piston.restore();
        assertFalse(this.paper());
    }

    @Test
    void paperValueTracksFileEditedThenReloaded() {
        this.piston.desired(false);
        this.piston.enforce();
        assertFalse(this.paper());

        this.paperReload(true); // admin set allow-piston-duplication: true and ran /paper reload
        assertTrue(this.piston.enforce());
        assertFalse(this.paper(), "plugin config wins while installed");
        assertTrue(this.piston.paperValue());

        this.piston.restore();
        assertTrue(this.paper(), "restores the file value");
    }

    @Test
    void bukkitReloadLoadsConfigBeforeDisableSoRestoreKeepsFreshValue() {
        // Plugin forces true over a file value of false...
        this.piston.desired(true);
        this.piston.enforce();
        assertFalse(this.piston.paperValue());

        // ...then the admin edits the file to true and runs /reload: CraftServer#reload loads Paper's
        // config first and only then calls onDisable.
        this.paperReload(true);
        this.piston.restore();
        assertTrue(this.paper(), "must not overwrite the freshly loaded file value with the stale snapshot");
    }

    @Test
    void bukkitReloadWithUnchangedFileLeavesFileValue() {
        this.piston.desired(true);
        this.piston.enforce();

        this.paperReload(false);
        this.piston.restore();
        assertFalse(this.paper());
    }

    @Test
    void unloadWithoutReloadRestores() {
        // e.g. a plugin manager unloading DupeEnabler
        this.piston.desired(true);
        this.piston.enforce();
        this.piston.restore();
        assertFalse(this.paper());
    }

    @Test
    void restoreLeavesForeignChangesAlone() {
        this.piston.desired(true);
        this.piston.enforce();

        this.config.unsupportedSettings.allowPistonDuplication = false; // changed in place after our write
        this.config.unsupportedSettings.allowPistonDuplication = true;
        this.piston.restore();
        assertFalse(this.paper(), "value still ours, so it is restored");

        this.piston.enforce();
        this.config.unsupportedSettings.allowPistonDuplication = false; // foreign value at disable time
        this.piston.desired(true);
        this.piston.restore();
        assertFalse(this.paper());

        this.config.unsupportedSettings.allowPistonDuplication = true;
        this.piston.restore(); // restore after restore is a no-op
        assertTrue(this.paper());
    }

    @Test
    void reEnableAfterRestoreSnapshotsAgain() {
        this.piston.desired(true);
        this.piston.enforce();
        this.piston.restore();

        this.paperReload(true); // file changed while plugin was disabled
        this.piston.desired(false);
        assertFalse(this.piston.enforce(), "first apply after re-enable is not a reload");
        assertFalse(this.paper());
        this.piston.restore();
        assertTrue(this.paper());
    }
}
