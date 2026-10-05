package io.github.jonahp4.dupeenabler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

/**
 * Live handle to one boolean in Paper's in-memory global configuration,
 * i.e. {@code GlobalConfiguration.get().<section>.<flag>}.
 *
 * <p>Paper's patched vanilla code reads these switches straight off the config object every time
 * it runs, so writing one takes effect immediately and adds no per-tick or per-event cost: Paper
 * simply takes its untouched vanilla branch.
 *
 * <p>Only the handles are cached, never the config objects: {@code /paper reload} loads into the
 * live config in place and may swap section objects, so every access walks the path again.
 */
final class PaperSwitch {

    static final String GLOBAL_CONFIGURATION = "io.papermc.paper.configuration.GlobalConfiguration";

    private final String path;
    private final MethodHandle root;    // () -> GlobalConfiguration, erased to Object
    private final MethodHandle section; // (GlobalConfiguration) -> section, erased to (Object)Object
    private final VarHandle flag;       // boolean field on the section

    private PaperSwitch(String path, MethodHandle root, MethodHandle section, VarHandle flag) {
        this.path = path;
        this.root = root;
        this.section = section;
        this.flag = flag;
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
        VarHandle flag = lookup.findVarHandle(sectionField0.getType(), flagField, boolean.class);

        return new PaperSwitch(sectionField + "." + flagField, root, section, flag);
    }

    /** Java field path of the setting, e.g. {@code unsupportedSettings.allowPistonDuplication}. */
    String path() {
        return this.path;
    }

    boolean get() {
        return (boolean) this.flag.get(this.section());
    }

    void set(boolean value) {
        this.flag.set(this.section(), value);
    }

    private Object section() {
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
