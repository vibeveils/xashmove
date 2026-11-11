package dev.xashmove.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.xashmove.XashMove;

/** C2S: whether this client is currently running Xash movement (cl_xash). */
public record ClientStatePayload(int protocol, boolean enabled) implements CustomPacketPayload {
	public static final Type<ClientStatePayload> TYPE = new Type<>(XashMove.id("client_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientStatePayload> CODEC =
			CustomPacketPayload.codec(ClientStatePayload::write, ClientStatePayload::read);

	private static ClientStatePayload read(RegistryFriendlyByteBuf buf) {
		return new ClientStatePayload(buf.readVarInt(), buf.readBoolean());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(protocol);
		buf.writeBoolean(enabled);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
