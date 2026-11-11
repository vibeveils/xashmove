package dev.xashmove.server;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionLevel;

import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;

import dev.xashmove.Mode;
import dev.xashmove.XashMove;
import dev.xashmove.cvar.Cvar;
import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;

/**
 * {@code /xash} - a tiny Xash-style server console:
 * <pre>
 * /xash                       status
 * /xash list [filter]         list cvars
 * /xash get &lt;cvar&gt;            show value + help
 * /xash set &lt;cvar&gt; &lt;value&gt;    change (synced to clients instantly)
 * /xash reset &lt;cvar|all&gt;
 * /xash preset &lt;name&gt;         hl1, xash, quake, cs16, kz, bhop, surf, vanilla_safe
 * /xash mode &lt;off|vanilla|pure&gt;
 * /xash reload                re-read config/xashmove-server.json
 * </pre>
 */
public final class XashCommands {
	private XashCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		Predicate<CommandSourceStack> admin = PermissionPredicates.require(XashMove.id("command.admin"), PermissionLevel.GAMEMASTERS);

		dispatcher.register(literal("xash")
				.executes(XashCommands::status)
				.then(literal("list")
						.executes(ctx -> list(ctx, ""))
						.then(argument("filter", StringArgumentType.word()).executes(ctx -> list(ctx, StringArgumentType.getString(ctx, "filter")))))
				.then(literal("get")
						.then(argument("cvar", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(), b))
								.executes(ctx -> get(ctx, StringArgumentType.getString(ctx, "cvar")))))
				.then(literal("set").requires(admin)
						.then(argument("cvar", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(), b))
								.then(argument("value", StringArgumentType.greedyString())
										.executes(ctx -> set(ctx, StringArgumentType.getString(ctx, "cvar"), StringArgumentType.getString(ctx, "value"))))))
				.then(literal("reset").requires(admin)
						.then(literal("all").executes(XashCommands::resetAll))
						.then(argument("cvar", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(), b))
								.executes(ctx -> reset(ctx, StringArgumentType.getString(ctx, "cvar")))))
				.then(literal("preset").requires(admin)
						.then(argument("name", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Cvars.presets().keySet(), b))
								.executes(ctx -> preset(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(literal("mode").requires(admin)
						.then(literal("off").executes(ctx -> mode(ctx, Mode.OFF)))
						.then(literal("vanilla").executes(ctx -> mode(ctx, Mode.VANILLA_FRIENDLY)))
						.then(literal("pure").executes(ctx -> mode(ctx, Mode.PURE))))
				.then(literal("reload").requires(admin).executes(ctx -> {
					XashServer.reload();
					ok(ctx, "Reloaded config/xashmove-server.json");
					return 1;
				})));
	}

	private static List<String> names() {
		List<String> out = new ArrayList<>();
		for (Cvar c : Cvars.all()) {
			if (c.scope != Cvar.Scope.CLIENT) out.add(c.name);
		}
		return out;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		Mode m = XashServer.mode();
		ok(ctx, "Xash Movement: mode " + m.label + ", " + XashServer.activeCount() + " player(s) running Xash physics. Try /xash list");
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx, String filter) {
		CvarStore cfg = XashServer.config();
		String f = filter.toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder();
		int n = 0;
		for (Cvar c : Cvars.all()) {
			if (c.scope == Cvar.Scope.CLIENT || !c.name.contains(f)) continue;
			if (n++ > 0) sb.append('\n');
			sb.append(c.name).append(" = ").append(c.format(cfg.get(c)));
			if (cfg.get(c) != c.def) sb.append("  (default ").append(c.format(c.def)).append(')');
		}
		String text = n == 0 ? "No cvars match '" + filter + "'" : sb.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return n;
	}

	private static int get(CommandContext<CommandSourceStack> ctx, String name) {
		Cvar c = Cvars.get(name);
		if (c == null || c.scope == Cvar.Scope.CLIENT) return fail(ctx, "Unknown server cvar '" + name + "'");
		CvarStore cfg = XashServer.config();
		ok(ctx, "\"" + c.name + "\" is \"" + c.format(cfg.get(c)) + "\" (default \"" + c.format(c.def) + "\", " + c.describeRange() + ")\n" + c.description);
		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> ctx, String name, String value) {
		Cvar c = Cvars.get(name);
		if (c == null || c.scope == Cvar.Scope.CLIENT) return fail(ctx, "Unknown server cvar '" + name + "'");
		try {
			XashServer.config().set(c, c.parse(value));
		} catch (IllegalArgumentException e) {
			return fail(ctx, e.getMessage());
		}
		XashServer.changed();
		ctx.getSource().sendSuccess(() -> Component.literal(c.name + " set to " + c.format(XashServer.config().get(c))), true);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx, String name) {
		Cvar c = Cvars.get(name);
		if (c == null || c.scope == Cvar.Scope.CLIENT) return fail(ctx, "Unknown server cvar '" + name + "'");
		XashServer.config().reset(c);
		XashServer.changed();
		ok(ctx, c.name + " reset to " + c.format(c.def));
		return 1;
	}

	private static int resetAll(CommandContext<CommandSourceStack> ctx) {
		XashServer.config().resetAll();
		XashServer.changed();
		ok(ctx, "All Xash server cvars reset to defaults");
		return 1;
	}

	private static int preset(CommandContext<CommandSourceStack> ctx, String name) {
		if (!XashServer.applyPreset(name.toLowerCase(Locale.ROOT))) {
			return fail(ctx, "Unknown preset '" + name + "'. Available: " + String.join(", ", Cvars.presets().keySet()));
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Applied movement preset " + name), true);
		return 1;
	}

	private static int mode(CommandContext<CommandSourceStack> ctx, Mode m) {
		XashServer.config().set(Cvars.SV_XASH_MODE, m.ordinal());
		XashServer.changed();
		ctx.getSource().sendSuccess(() -> Component.literal("Xash movement mode: " + m.label), true);
		return 1;
	}

	private static void ok(CommandContext<CommandSourceStack> ctx, String msg) {
		ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
		ctx.getSource().sendFailure(Component.literal(msg).withStyle(ChatFormatting.RED));
		return 0;
	}
}
