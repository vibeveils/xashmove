package dev.xashmove.net;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.xashmove.XashMove;

/** S2C: server mode + movevars (GoldSrc sends movevars_t to every client the same way). */
public record ServerConfigPayload(int protocol, int mode, Map<String, Double> movevars) implements CustomPacketPayload {
	public static final Type<ServerConfigPayload> TYPE = new Type<>(XashMove.id("server_config"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigPayload> CODEC =
			CustomPacketPayload.codec(ServerConfigPayload::write, ServerConfigPayload::read);

	private static ServerConfigPayload read(RegistryFriendlyByteBuf buf) {
		int protocol = buf.readVarInt();
		int mode = buf.readVarInt();
		int n = buf.readVarInt();
		if (n < 0 || n > 512) throw new IllegalArgumentException("Too many movevars: " + n);
		Map<String, Double> vars = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			String k = buf.readUtf(64);
			vars.put(k, buf.readDouble());
		}
		return new ServerConfigPayload(protocol, mode, vars);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(protocol);
		buf.writeVarInt(mode);
		buf.writeVarInt(movevars.size());
		movevars.forEach((k, v) -> {
			buf.writeUtf(k, 64);
			buf.writeDouble(v);
		});
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
