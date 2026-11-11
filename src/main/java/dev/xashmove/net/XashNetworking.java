package dev.xashmove.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class XashNetworking {
	private XashNetworking() {
	}

	public static void registerTypes() {
		PayloadTypeRegistry.clientboundPlay().register(ServerConfigPayload.TYPE, ServerConfigPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ClientStatePayload.TYPE, ClientStatePayload.CODEC);
	}
}
