package dev.xashmove.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import dev.xashmove.cvar.Cvar;
import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;
import dev.xashmove.server.XashServer;

/**
 * In-game options: every cvar, grouped by category, with click-to-cycle toggles and -/+ for numbers.
 * Server cvars edit the integrated server in singleplayer, the local profile when the server has no mod,
 * and are read-only on a modded multiplayer server (use /xash there).
 */
public final class XashConfigScreen extends Screen {
	private static final String[] CATEGORIES = {"style", "general", "movement", "combat", "weapons", "hud", "view", "animation", "server"};
	private static final String[] LABELS = {"Style", "General", "Movement", "Combat", "Weapons", "HUD", "View", "Animation", "Server"};
	private static final int[] TEXT_COLORS = {0xFFFFFFFF};

	private final Screen parent;
	private int category;
	private int page;
	private String status = "";

	public XashConfigScreen(Screen parent) {
		super(Component.literal("Xash Movement"));
		this.parent = parent;
	}

	// ------------------------------------------------------------------ where server cvars live right now
	private enum Target { SINGLEPLAYER, LOCAL_PROFILE, REMOTE_READONLY }

	private Target serverTarget() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.hasSingleplayerServer()) return Target.SINGLEPLAYER;
		if (ClientState.serverInfo() != null) return Target.REMOTE_READONLY;
		return Target.LOCAL_PROFILE;
	}

	private CvarStore storeFor(Cvar c) {
		if (c.scope == Cvar.Scope.CLIENT) return ClientState.config();
		if (serverTarget() == Target.SINGLEPLAYER || c.scope == Cvar.Scope.SERVER_ONLY) return XashServer.config();
		return ClientState.config();
	}

	private boolean editable(Cvar c) {
		if (c.scope == Cvar.Scope.CLIENT) return true;
		Target t = serverTarget();
		if (c.scope == Cvar.Scope.SERVER_ONLY) return t == Target.SINGLEPLAYER;
		return t != Target.REMOTE_READONLY;
	}

	private void commit(Cvar c) {
		if (c.scope != Cvar.Scope.CLIENT && serverTarget() == Target.SINGLEPLAYER) {
			IntegratedServer sp = Minecraft.getInstance().getSingleplayerServer();
			if (sp != null) sp.execute(XashServer::changed);
		} else {
			ClientState.changed();
		}
	}

	// ------------------------------------------------------------------ layout
	@Override
	protected void init() {
		int tabW = Math.min(70, (this.width - 20) / CATEGORIES.length);
		int x0 = (this.width - tabW * CATEGORIES.length) / 2;
		for (int i = 0; i < CATEGORIES.length; i++) {
			final int idx = i;
			Button tab = Button.builder(Component.literal(LABELS[i]), b -> {
				category = idx;
				page = 0;
				rebuild();
			}).pos(x0 + i * tabW, 22).size(tabW - 2, 20).build();
			tab.active = i != category;
			addRenderableWidget(tab);
		}

		if (CATEGORIES[category].equals("style")) {
			initStyles();
		} else {
			initCvars();
		}

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.pos(this.width / 2 - 75, this.height - 26).size(150, 20).build());
	}

	private void initStyles() {
		int y = 52;
		int cx = this.width / 2;
		String[][] styles = {
				{"vanilla", "Pure Minecraft: Xash movement off, no HL effects"},
				{"movement", "Xash movement only, Minecraft look and feel"},
				{"classic", "Movement + HL view bob, crosshair, ammo, GoldSrc animations"},
				{"full", "Everything HL: weapon buckets, HL HUD, pickup history, damage flash, net_graph"},
		};
		for (String[] st : styles) {
			addRenderableWidget(Button.builder(Component.literal("Client style: " + st[0]), b -> {
				ClientState.config().applyStyle(st[0]);
				ClientState.changed();
				status = "Applied client style " + st[0];
			}).pos(cx - 155, y).size(150, 20).tooltip(Tooltip.create(Component.literal(st[1]))).build());
			y += 24;
		}

		y = 52;
		boolean canEditServer = serverTarget() != Target.REMOTE_READONLY;
		for (String preset : Cvars.presets().keySet()) {
			Button b = Button.builder(Component.literal("Preset: " + preset), btn -> applyPreset(preset))
					.pos(cx + 5, y).size(150, 20)
					.tooltip(Tooltip.create(Component.literal(serverTarget() == Target.SINGLEPLAYER
							? "Applies to this world's server config" : "Applies to your local profile (servers without the mod)")))
					.build();
			b.active = canEditServer;
			addRenderableWidget(b);
			y += 22;
			if (y > this.height - 50) break;
		}
	}

	private void applyPreset(String preset) {
		if (serverTarget() == Target.SINGLEPLAYER) {
			IntegratedServer sp = Minecraft.getInstance().getSingleplayerServer();
			if (sp != null) sp.execute(() -> XashServer.applyPreset(preset));
		} else {
			ClientState.config().applyPreset(preset);
			ClientState.changed();
		}
		status = "Applied preset " + preset;
	}

	private void initCvars() {
		List<Cvar> list = new ArrayList<>();
		for (Cvar c : Cvars.all()) {
			if (c.category().equals(CATEGORIES[category])) list.add(c);
		}
		int rowH = 22;
		int top = 50;
		int perColumn = Math.max(1, (this.height - top - 34) / rowH);
		boolean twoColumns = this.width >= 520;
		int perPage = perColumn * (twoColumns ? 2 : 1);
		int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
		page = Math.min(page, pages - 1);

		int colW = twoColumns ? 250 : Math.min(300, this.width - 20);
		int startX = twoColumns ? this.width / 2 - colW - 5 : (this.width - colW) / 2;
		for (int i = page * perPage; i < Math.min(list.size(), (page + 1) * perPage); i++) {
			int local = i - page * perPage;
			int col = local / perColumn, row = local % perColumn;
			addRow(list.get(i), startX + col * (colW + 10), top + row * rowH, colW);
		}

		if (pages > 1) {
			Button prev = Button.builder(Component.literal("<"), b -> {
				page--;
				rebuild();
			}).pos(this.width / 2 - 100, this.height - 26).size(20, 20).build();
			prev.active = page > 0;
			addRenderableWidget(prev);
			Button next = Button.builder(Component.literal(">"), b -> {
				page++;
				rebuild();
			}).pos(this.width / 2 + 80, this.height - 26).size(20, 20).build();
			next.active = page < pages - 1;
			addRenderableWidget(next);
		}
	}

	private void addRow(Cvar c, int x, int y, int w) {
		boolean numeric = c.type == Cvar.Type.FLOAT || (c.type == Cvar.Type.INT && c != Cvars.HUD_COLOR);
		boolean canEdit = editable(c);
		int mainW = numeric ? w - 44 : w;
		String where = c.scope == Cvar.Scope.CLIENT ? "" : switch (serverTarget()) {
			case SINGLEPLAYER -> "\n[this world's server config]";
			case LOCAL_PROFILE -> c.scope == Cvar.Scope.SERVER ? "\n[local profile, used on servers without the mod]" : "\n[only editable in singleplayer]";
			case REMOTE_READONLY -> "\n[set by this server - ask an admin to use /xash set]";
		};
		Button main = Button.builder(label(c), b -> {
			CvarStore st = storeFor(c);
			double v = st.get(c);
			if (c.type == Cvar.Type.BOOL) v = v != 0 ? 0 : 1;
			else if (c.type == Cvar.Type.ENUM) v = (v + 1) > c.max ? c.min : v + 1;
			else if (c == Cvars.HUD_COLOR) v = nextColor((int) v);
			else v = v + c.step() > c.max + 1e-9 ? c.min : v + c.step();
			st.set(c, v);
			commit(c);
			b.setMessage(label(c));
		}).pos(x, y).size(mainW, 20).tooltip(Tooltip.create(Component.literal(c.name + "\n" + c.description
				+ "\nDefault: " + c.format(c.def) + "  Range: " + c.describeRange() + where))).build();
		main.active = canEdit;
		addRenderableWidget(main);

		if (numeric) {
			Button minus = Button.builder(Component.literal("-"), b -> nudge(c, -1, main)).pos(x + mainW + 2, y).size(20, 20).build();
			Button plus = Button.builder(Component.literal("+"), b -> nudge(c, 1, main)).pos(x + mainW + 24, y).size(20, 20).build();
			minus.active = canEdit;
			plus.active = canEdit;
			addRenderableWidget(minus);
			addRenderableWidget(plus);
		}
	}

	private void nudge(Cvar c, int dir, Button main) {
		CvarStore st = storeFor(c);
		st.set(c, st.get(c) + dir * c.step());
		commit(c);
		main.setMessage(label(c));
	}

	private static final int[] PALETTE = {0xFFA000, 0x00FF60, 0x40C0FF, 0xFF3030, 0xFFFFFF, 0xFFE000};

	private static int nextColor(int current) {
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == current) return PALETTE[(i + 1) % PALETTE.length];
		}
		return PALETTE[0];
	}

	private Component label(Cvar c) {
		double v = storeFor(c).get(c);
		if (c.scope == Cvar.Scope.SERVER && serverTarget() == Target.REMOTE_READONLY && ClientState.serverInfo() != null) {
			Double synced = ClientState.serverInfo().movevars().get(c.name);
			if (synced != null) v = synced;
		}
		String text = c.name + ": " + c.format(v);
		if (c.scope == Cvar.Scope.SERVER_ONLY && serverTarget() != Target.SINGLEPLAYER) text = c.name + ": (server)";
		return Component.literal(text);
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// super already draws the (blurred) background in 26.3; drawing it again crashes ("Can only blur once per frame")
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		String header = "Xash Movement - mode: " + ClientState.modeDescription();
		graphics.centeredText(this.font, header, this.width / 2, 8, TEXT_COLORS[0]);
		if (!status.isEmpty()) graphics.centeredText(this.font, status, this.width / 2, this.height - 40, 0xFFFFA000);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}
}
