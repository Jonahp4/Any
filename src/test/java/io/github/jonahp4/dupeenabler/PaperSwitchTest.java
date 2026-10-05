package io.github.jonahp4.dupeenabler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.configuration.GlobalConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperSwitchTest {

    private GlobalConfiguration config;

    @BeforeEach
    void setUp() {
        this.config = new GlobalConfiguration();
        GlobalConfiguration.set(this.config);
    }

    @AfterEach
    void tearDown() {
        GlobalConfiguration.set(null);
    }

    @Test
    void everyDupeResolvesAgainstPaperShape() throws ReflectiveOperationException {
        for (Dupe dupe : Dupe.values()) {
            PaperSwitch handle = PaperSwitch.resolve(dupe.section(), dupe.field());
            assertFalse(handle.get(), dupe.id());
            handle.set(true);
            assertTrue(handle.get(), dupe.id());
        }
        assertTrue(this.config.unsupportedSettings.allowPistonDuplication);
        assertTrue(this.config.unsupportedSettings.skipTripwireHookPlacementValidation);
        assertTrue(this.config.unsupportedSettings.allowUnsafeEndPortalTeleportation);
    }

    @Test
    void writesGoToTheLiveObjectPaperReads() throws ReflectiveOperationException {
        PaperSwitch piston = PaperSwitch.resolve("unsupportedSettings", "allowPistonDuplication");
        piston.set(true);
        assertTrue(GlobalConfiguration.get().unsupportedSettings.allowPistonDuplication);
        assertFalse(GlobalConfiguration.get().unsupportedSettings.skipTripwireHookPlacementValidation);
    }

    @Test
    void followsReplacedSectionAndRootObjects() throws ReflectiveOperationException {
        PaperSwitch piston = PaperSwitch.resolve("unsupportedSettings", "allowPistonDuplication");
        piston.set(true);

        // A reload that swaps the section object must not leave us writing to a stale one.
        this.config.unsupportedSettings = this.config.new UnsupportedSettings();
        assertFalse(piston.get());
        piston.set(true);
        assertTrue(this.config.unsupportedSettings.allowPistonDuplication);

        // Same for the root object.
        GlobalConfiguration replacement = new GlobalConfiguration();
        GlobalConfiguration.set(replacement);
        assertFalse(piston.get());
        piston.set(true);
        assertTrue(replacement.unsupportedSettings.allowPistonDuplication);
    }

    @Test
    void missingSettingFailsAtResolveTime() {
        assertThrows(NoSuchFieldException.class, () -> PaperSwitch.resolve("unsupportedSettings", "doesNotExist"));
        assertThrows(NoSuchFieldException.class, () -> PaperSwitch.resolve("noSuchSection", "allowPistonDuplication"));
    }

    @Test
    void unloadedConfigIsAnIllegalState() throws ReflectiveOperationException {
        PaperSwitch piston = PaperSwitch.resolve("unsupportedSettings", "allowPistonDuplication");
        GlobalConfiguration.set(null);
        assertThrows(IllegalStateException.class, piston::get);
        assertThrows(IllegalStateException.class, () -> piston.set(true));
    }

    @Test
    void dupeLookupIsCaseInsensitive() {
        assertSame(Dupe.PISTON, Dupe.byId("PiStOn"));
        assertSame(Dupe.TRIPWIRE, Dupe.byId("tripwire"));
        assertSame(Dupe.GRAVITY, Dupe.byId("GRAVITY"));
        assertNull(Dupe.byId("sand"));
        assertTrue(Dupe.PISTON.enabledByDefault());
        assertTrue(Dupe.TRIPWIRE.enabledByDefault());
        assertFalse(Dupe.GRAVITY.enabledByDefault());
        assertEquals("dupes.piston", Dupe.PISTON.configPath());
    }
}
