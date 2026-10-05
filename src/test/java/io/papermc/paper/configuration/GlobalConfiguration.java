package io.papermc.paper.configuration;

/**
 * Test double with the same shape as Paper 1.21.11's GlobalConfiguration: a static get(), public
 * section fields holding non-static inner classes, and public non-final boolean switches.
 */
public class GlobalConfiguration {

    private static GlobalConfiguration instance;

    public static GlobalConfiguration get() {
        return instance;
    }

    public static void set(GlobalConfiguration configuration) {
        instance = configuration;
    }

    public UnsupportedSettings unsupportedSettings = new UnsupportedSettings();
    public BlockUpdates blockUpdates = new BlockUpdates();

    public class UnsupportedSettings {
        public boolean allowUnsafeEndPortalTeleportation = false;
        public boolean allowPistonDuplication = false;
        public boolean skipTripwireHookPlacementValidation = false;
    }

    public class BlockUpdates {
        public boolean disableTripwireUpdates = false;
    }
}
