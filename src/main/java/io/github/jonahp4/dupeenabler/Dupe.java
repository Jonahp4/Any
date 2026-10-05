package io.github.jonahp4.dupeenabler;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The duping mechanics this plugin manages. Each one maps 1:1 onto a switch Paper itself keeps in
 * {@code paper-global.yml} under {@code unsupported-settings}; with the switch on, Paper runs the
 * vanilla code path, so contraptions behave exactly like they do in singleplayer.
 *
 * <p>Each switch is read in exactly one place in Paper 1.21.11 (two for {@link #PISTON}), so turning
 * one on unlocks only the mechanic described here.
 */
enum Dupe {

    /**
     * Gates Paper's "fix a variety of piston desync dupes" patch in {@code PistonBaseBlock#moveBlocks}
     * (and a client-sync flag in {@code PistonMovingBlockEntity#tick}). Vanilla builds each moving
     * block from the states it captured before the push, so a block that is used up mid-push
     * (TNT primed by the update, carpet or rail losing its support) is still placed at the end.
     */
    PISTON(
        "piston",
        "unsupportedSettings", "allowPistonDuplication",
        "unsupported-settings.allow-piston-duplication",
        "TNT, carpet & rail duping with pistons",
        true
    ),

    /**
     * Gates Paper's "validate tripwire hook placement before update" check in
     * {@code TripWireHookBlock#calculateState}. Vanilla writes the hook that started the update back
     * to its position without checking it is still there; if the update chain popped it off (e.g.
     * its trapdoor support moved), the hook drops as an item and is placed again.
     *
     * <p>String duping is not part of this: Mojang removed it from vanilla in 1.21.2 (24w33a), so no
     * server setting can bring it back.
     */
    TRIPWIRE(
        "tripwire",
        "unsupportedSettings", "skipTripwireHookPlacementValidation",
        "unsupported-settings.skip-tripwire-hook-placement-validation",
        "Tripwire hook duping",
        true
    ),

    /**
     * Gates vanilla's own {@code FallingBlockEntity#forceTickAfterTeleportToDuplicate}: a falling
     * block that goes through an end portal is ticked once more in the old dimension and lands
     * there too. Despite the name, this is the setting's only use in Paper 1.21.11. Off by default
     * because it was not part of the original request.
     */
    GRAVITY(
        "gravity",
        "unsupportedSettings", "allowUnsafeEndPortalTeleportation",
        "unsupported-settings.allow-unsafe-end-portal-teleportation",
        "Sand, gravel & concrete powder duping through end portals",
        false
    );

    private static final Dupe[] VALUES = values();

    private final String id;
    private final String section;
    private final String field;
    private final String paperSetting;
    private final String description;
    private final boolean enabledByDefault;

    Dupe(String id, String section, String field, String paperSetting, String description, boolean enabledByDefault) {
        this.id = id;
        this.section = section;
        this.field = field;
        this.paperSetting = paperSetting;
        this.description = description;
        this.enabledByDefault = enabledByDefault;
    }

    /** Short name used in {@code config.yml} and commands. */
    String id() {
        return this.id;
    }

    /** Field holding the section object on {@code GlobalConfiguration}. */
    String section() {
        return this.section;
    }

    /** Boolean field on the section object. */
    String field() {
        return this.field;
    }

    /** The equivalent key in {@code paper-global.yml}. */
    String paperSetting() {
        return this.paperSetting;
    }

    String description() {
        return this.description;
    }

    /** Value used when {@code config.yml} does not mention this dupe. */
    boolean enabledByDefault() {
        return this.enabledByDefault;
    }

    String configPath() {
        return "dupes." + this.id;
    }

    static @Nullable Dupe byId(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        for (Dupe dupe : VALUES) {
            if (dupe.id.equals(normalized)) {
                return dupe;
            }
        }
        return null;
    }
}
