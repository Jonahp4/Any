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

    private void paperReload(boolean fileValue) {
        // What Configurations.reloader does: load the file into the existing objects.
        this.config.unsupportedSettings.allowPistonDuplication = fileValue;
    }

    @Test
    void enableRemembersPaperValue() {
        this.piston.desired(true);
        assertFalse(this.piston.enforce(), "first apply is not a correction");
        assertTrue(this.paper());
        assertFalse(this.piston.paperValue());

        this.piston.restore();
        assertFalse(this.paper());
    }

    @Test
    void reapplyAfterPaperReload() {
        this.piston.desired(true);
        this.piston.enforce();

        this.paperReload(false);
        assertTrue(this.piston.enforce(), "reset by reload must be reported");
        assertTrue(this.paper());
        assertFalse(this.piston.enforce(), "steady state is quiet");
        assertTrue(this.paper());
    }

    @Test
    void steadyStateDoesNotWrite() {
        this.piston.desired(true);
        this.piston.enforce();
        for (int i = 0; i < 1000; i++) {
            assertFalse(this.piston.enforce());
        }
        assertTrue(this.paper());
    }

    @Test
    void togglingOffDoesNotForgetPaperValue() {
        this.piston.desired(true);
        this.piston.enforce();

        this.piston.desired(false);
        assertFalse(this.piston.enforce(), "our own change is not an external reset");
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
    void restoreLeavesForeignChangesAlone() {
        this.piston.desired(true);
        this.piston.enforce();

        this.paperReload(false); // someone else changed it after our last write
        this.piston.restore();
        assertFalse(this.paper());

        this.paperReload(true);
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
        this.piston.enforce();
        assertFalse(this.paper());
        this.piston.restore();
        assertTrue(this.paper());
    }
}
