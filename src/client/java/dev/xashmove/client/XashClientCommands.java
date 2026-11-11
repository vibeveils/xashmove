package dev.xashmove.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import dev.xashmove.cvar.Cvar;
import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;

/**
 * {@code /xashc} - client console. Edits cl_* / hud_* prefs and the local sv_* profile that is used on
 * servers without the mod (vanilla-friendly mode). On a modded server the server's sv_* values win.
 */
public final class XashClientCommands {
	private XashClientCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(literal("xashc")
				.executes(XashClientCommands::status)
				.then(literal("list")
						.executes(ctx -> list(ctx, ""))
						.then(argument("filter", StringArgumentType.word()).executes(ctx -> list(ctx, StringArgumentType.getString(ctx, "filter")))))
				.then(literal("get")
						.then(argument("cvar", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(), b))
								.executes(ctx -> get(ctx, StringArgumentType.getString(ctx, "cvar")))))
				.then(literal("set")
						.then(argument("cvar", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(), b))
								.then(argument("value", StringArgumentType.greedyString())
										.executes(ctx -> set(ctx, StringArgumentType.getString(ctx, "cvar"), StringArgumentType.getString(ctx, "value"))))))
				.then(literal("toggle")
						.then(argument("cvar", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(), b))
								.executes(ctx -> toggle(ctx, StringArgumentType.getString(ctx, "cvar")))))
				.then(literal("reset")
						.then(literal("all").executes(ctx -> {
							ClientState.config().resetAll();
							ClientState.changed();
							ok(ctx, "All client cvars reset");
							return 1;
						}))
						.then(argument("cvar", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(), b))
								.executes(ctx -> reset(ctx, StringArgumentType.getString(ctx, "cvar")))))
				.then(literal("preset")
						.then(argument("name", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Cvars.presets().keySet(), b))
								.executes(ctx -> preset(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(literal("menu").executes(ctx -> {
					XashMoveClient.requestMenu();
					return 1;
				}))
				.then(literal("style")
						.then(argument("name", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Cvars.styles().keySet(), b))
								.executes(ctx -> style(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(literal("reload").executes(ctx -> {
					ClientState.config().load();
					ClientState.changed();
					ok(ctx, "Reloaded config/xashmove-client.json");
					return 1;
				})));
	}

	private static List<String> names() {
		List<String> out = new ArrayList<>();
		for (Cvar c : Cvars.all()) {
			if (ClientState.config().owns(c)) out.add(c.name);
		}
		return out;
	}

	private static Cvar find(String name) {
		Cvar c = Cvars.get(name);
		return ClientState.config().owns(c) ? c : null;
	}

	private static int status(CommandContext<FabricClientCommandSource> ctx) {
		String server = ClientState.serverInfo() != null ? "server runs Xash Movement" : "server has no Xash Movement";
		ok(ctx, "Xash movement: " + ClientState.modeDescription() + " - " + server + ". /xashc list for cvars, /xash for server cvars");
		return 1;
	}

	private static int list(CommandContext<FabricClientCommandSource> ctx, String filter) {
		CvarStore cfg = ClientState.config();
		String f = filter.toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder();
		int n = 0;
		for (Cvar c : Cvars.all()) {
			if (!cfg.owns(c) || !c.name.contains(f)) continue;
			if (n++ > 0) sb.append('\n');
			sb.append(c.name).append(" = ").append(c.format(cfg.get(c)));
			if (c.scope == Cvar.Scope.SERVER) sb.append("  [local profile]");
		}
		ctx.getSource().sendFeedback(Component.literal(n == 0 ? "No cvars match '" + filter + "'" : sb.toString()));
		return n;
	}

	private static int get(CommandContext<FabricClientCommandSource> ctx, String name) {
		Cvar c = find(name);
		if (c == null) return fail(ctx, "Unknown client cvar '" + name + "'");
		ok(ctx, "\"" + c.name + "\" is \"" + c.format(ClientState.config().get(c)) + "\" (default \"" + c.format(c.def) + "\", "
				+ c.describeRange() + ")\n" + c.description);
		return 1;
	}

	private static int set(CommandContext<FabricClientCommandSource> ctx, String name, String value) {
		Cvar c = find(name);
		if (c == null) return fail(ctx, "Unknown client cvar '" + name + "'");
		try {
			ClientState.config().set(c, c.parse(value));
		} catch (IllegalArgumentException e) {
			return fail(ctx, e.getMessage());
		}
		ClientState.changed();
		String note = c.scope == Cvar.Scope.SERVER && ClientState.serverInfo() != null ? " (local profile; this server's value is in use)" : "";
		ok(ctx, c.name + " = " + c.format(ClientState.config().get(c)) + note);
		return 1;
	}

	private static int toggle(CommandContext<FabricClientCommandSource> ctx, String name) {
		Cvar c = find(name);
		if (c == null || c.type != Cvar.Type.BOOL) return fail(ctx, "'" + name + "' is not a 0/1 client cvar");
		ClientState.config().set(c, ClientState.config().bool(c) ? 0 : 1);
		ClientState.changed();
		ok(ctx, c.name + " = " + c.format(ClientState.config().get(c)));
		return 1;
	}

	private static int reset(CommandContext<FabricClientCommandSource> ctx, String name) {
		Cvar c = find(name);
		if (c == null) return fail(ctx, "Unknown client cvar '" + name + "'");
		ClientState.config().reset(c);
		ClientState.changed();
		ok(ctx, c.name + " reset to " + c.format(c.def));
		return 1;
	}

	private static int preset(CommandContext<FabricClientCommandSource> ctx, String name) {
		if (!ClientState.config().applyPreset(name.toLowerCase(Locale.ROOT))) {
			return fail(ctx, "Unknown preset. Available: " + String.join(", ", Cvars.presets().keySet()));
		}
		ClientState.changed();
		ok(ctx, "Local profile set to preset " + name + (ClientState.serverInfo() != null ? " (used on vanilla servers; this server sets its own)" : ""));
		return 1;
	}

	private static int style(CommandContext<FabricClientCommandSource> ctx, String name) {
		if (!ClientState.config().applyStyle(name.toLowerCase(Locale.ROOT))) {
			return fail(ctx, "Unknown style. Available: " + String.join(", ", Cvars.styles().keySet()));
		}
		ClientState.changed();
		ok(ctx, "Client style: " + name + " (fine-tune with /xashc menu)");
		return 1;
	}

	private static void ok(CommandContext<FabricClientCommandSource> ctx, String msg) {
		ctx.getSource().sendFeedback(Component.literal(msg));
	}

	private static int fail(CommandContext<FabricClientCommandSource> ctx, String msg) {
		ctx.getSource().sendError(Component.literal(msg).withStyle(ChatFormatting.RED));
		return 0;
	}
}
