package io.github.jonahp4.dupeenabler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * Live handle to one boolean in Paper's in-memory global configuration,
 * i.e. {@code GlobalConfiguration.get().<section>.<flag>}.
 *
 * <p>Paper's patched vanilla code reads these switches straight off the config object every time
 * it runs, so writing one takes effect immediately and adds no per-tick or per-event cost: Paper
 * simply takes its untouched vanilla branch.
 *
 * <p>All handles are adapted to exact erased types once, so each access is a direct
 * {@code invokeExact} the JIT can inline: no reflection, boxing or allocation per call.
 *
 * <p>Only the handles are cached, never the config objects: {@code /paper reload} (and Bukkit's
 * {@code /reload}) replaces the section objects with freshly loaded ones, so every access walks the
 * path again. That replacement is also what {@link ManagedSwitch} uses to notice a reload.
 */
final class PaperSwitch {

    static final String GLOBAL_CONFIGURATION = "io.papermc.paper.configuration.GlobalConfiguration";

    private final String path;
    private final MethodHandle root;    // () -> GlobalConfiguration, erased to Object
    private final MethodHandle section; // (GlobalConfiguration) -> section, erased to (Object)Object
    private final MethodHandle getter;  // (section) -> boolean, erased to (Object)boolean
    private final MethodHandle setter;  // (section, boolean) -> void, erased to (Object,boolean)void

    private PaperSwitch(String path, MethodHandle root, MethodHandle section, MethodHandle getter, MethodHandle setter) {
        this.path = path;
        this.root = root;
        this.section = section;
        this.getter = getter;
        this.setter = setter;
    }

    /**
     * Resolves {@code GlobalConfiguration.get().<sectionField>.<flagField>}.
     *
     * @throws ReflectiveOperationException if this Paper build has no such setting
     */
    static PaperSwitch resolve(String sectionField, String flagField) throws ReflectiveOperationException {
        Class<?> global = Class.forName(GLOBAL_CONFIGURATION);
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodHandle root = lookup.findStatic(global, "get", MethodType.methodType(global))
            .asType(MethodType.methodType(Object.class));
        Field sectionField0 = global.getField(sectionField);
        MethodHandle section = lookup.unreflectGetter(sectionField0)
            .asType(MethodType.methodType(Object.class, Object.class));
        // Take the section type from the field, never by simple name: WorldConfiguration has its own
        // UnsupportedSettings class too.
        Class<?> sectionType = sectionField0.getType();
        MethodHandle getter = lookup.findGetter(sectionType, flagField, boolean.class)
            .asType(MethodType.methodType(boolean.class, Object.class));
        MethodHandle setter = lookup.findSetter(sectionType, flagField, boolean.class)
            .asType(MethodType.methodType(void.class, Object.class, boolean.class));

        return new PaperSwitch(sectionField + "." + flagField, root, section, getter, setter);
    }

    /** Java field path of the setting, e.g. {@code unsupportedSettings.allowPistonDuplication}. */
    String path() {
        return this.path;
    }

    boolean get() {
        return this.get(this.section());
    }

    void set(boolean value) {
        this.set(this.section(), value);
    }

    boolean get(Object section) {
        try {
            return (boolean) this.getter.invokeExact(section);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Could not read " + this.path, t);
        }
    }

    void set(Object section, boolean value) {
        try {
            this.setter.invokeExact(section, value);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Could not write " + this.path, t);
        }
    }

    /** The live section object holding this switch. Paper swaps it for a new one on every config reload. */
    Object section() {
        try {
            Object config = (Object) this.root.invokeExact();
            if (config == null) {
                throw new IllegalStateException("Paper's global configuration is not loaded yet");
            }
            Object section = (Object) this.section.invokeExact(config);
            if (section == null) {
                throw new IllegalStateException("Paper's global configuration has no section for " + this.path);
            }
            return section;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Could not read " + this.path, t);
        }
    }
}
