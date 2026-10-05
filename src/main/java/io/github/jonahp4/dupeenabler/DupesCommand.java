package io.github.jonahp4.dupeenabler;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

/**
 * {@code /dupes [status | reload | <piston|tripwire|gravity|all> [on|off|toggle]]}
 */
final class DupesCommand implements BasicCommand {

    static final String PERMISSION = "dupeenabler.admin";
    private static final String ALL = "all";
    private static final List<String> STATES = List.of("on", "off", "toggle");

    private final DupeEnablerPlugin plugin;

    DupesCommand(DupeEnablerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            this.status(sender);
            return;
        }

        String target = args[0].toLowerCase(Locale.ROOT);
        if (target.equals("reload")) {
            this.plugin.reload();
            sender.sendMessage(text("Reloaded config.yml.", GREEN));
            this.status(sender);
            return;
        }

        List<ManagedSwitch> targets = new ArrayList<>();
        if (target.equals(ALL)) {
            for (Dupe dupe : Dupe.values()) {
                ManagedSwitch managed = this.plugin.managed(dupe);
                if (managed != null) {
                    targets.add(managed);
                }
            }
        } else {
            Dupe dupe = Dupe.byId(target);
            if (dupe == null) {
                this.usage(sender);
                return;
            }
            ManagedSwitch managed = this.plugin.managed(dupe);
            if (managed == null) {
                sender.sendMessage(text(DupeEnablerPlugin.capitalize(dupe.id()) + " duping is not supported by this server.", RED));
                return;
            }
            targets.add(managed);
        }

        String state = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "toggle";
        final @Nullable Boolean enable; // null = toggle
        switch (state) {
            case "on", "true", "enable" -> enable = Boolean.TRUE;
            case "off", "false", "disable" -> enable = Boolean.FALSE;
            case "toggle" -> enable = null;
            default -> {
                this.usage(sender);
                return;
            }
        }

        // Toggling "all" flips them together: everything on unless everything already is.
        boolean allOn = targets.stream().allMatch(ManagedSwitch::desired);
        for (ManagedSwitch managed : targets) {
            boolean value = enable != null ? enable : (target.equals(ALL) ? !allOn : !managed.desired());
            this.plugin.setEnabled(managed, value);
            sender.sendMessage(this.line(managed));
            if (managed.desired()) {
                this.problems(sender, managed.dupe());
            }
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            List<String> options = new ArrayList<>(List.of("status", "reload", ALL));
            for (Dupe dupe : Dupe.values()) {
                options.add(dupe.id());
            }
            return filter(options, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase(ALL) || Dupe.byId(args[0]) != null)) {
            return filter(STATES, args[1]);
        }
        return List.of();
    }

    @Override
    public @Nullable String permission() {
        return PERMISSION;
    }

    private void status(CommandSender sender) {
        sender.sendMessage(text("DupeEnabler ", GOLD).append(text("v" + this.plugin.getPluginMeta().getVersion(), GRAY)));
        for (Dupe dupe : Dupe.values()) {
            ManagedSwitch managed = this.plugin.managed(dupe);
            if (managed == null) {
                sender.sendMessage(text(" " + dupe.id() + ": ", GRAY).append(text("not supported by this server", RED)));
                continue;
            }
            sender.sendMessage(this.line(managed));
            if (managed.desired() != managed.live()) {
                sender.sendMessage(text("  ! Paper is currently using " + managed.live()
                    + " (watchdog-interval-ticks is 0?); run /dupes reload to re-apply", YELLOW));
            }
            if (managed.desired()) {
                this.problems(sender, dupe);
                Set<String> blockers = this.plugin.possibleBlockers(dupe);
                if (!blockers.isEmpty()) {
                    sender.sendMessage(text("  Listening to events that could block it: " + String.join(", ", blockers), GRAY));
                }
            }
        }
        sender.sendMessage(text("/dupes <piston|tripwire|gravity|all> [on|off|toggle]  ·  /dupes reload", GRAY));
    }

    private void problems(CommandSender sender, Dupe dupe) {
        for (String problem : this.plugin.problems(dupe)) {
            sender.sendMessage(text("  ! " + problem, YELLOW));
        }
    }

    private Component line(ManagedSwitch managed) {
        Dupe dupe = managed.dupe();
        return text(" " + dupe.id() + ": ", GRAY)
            .append(managed.desired() ? text("ENABLED", GREEN) : text("disabled", RED))
            .append(text("  " + dupe.description(), GRAY));
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(text("Usage: /dupes [status | reload | <piston|tripwire|gravity|all> [on|off|toggle]]", RED));
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>(options.size());
        for (String option : options) {
            if (option.startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
