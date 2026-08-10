package dev.failzockt.factoryrouting.network;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.utility.AdventureUtil;

import dev.failzockt.factoryrouting.FactoryRouting;
import dev.failzockt.factoryrouting.compat.factorycontroller.FactoryControllerNetwork;
import dev.failzockt.factoryrouting.routing.IngredientRoute;
import dev.failzockt.factoryrouting.routing.PanelRouting;
import dev.failzockt.factoryrouting.routing.RoutingAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.fml.ModList;

/** Client/server transport for reading and changing per-ingredient routes. */
public final class FactoryRoutingNetwork {

	private static final String PROTOCOL_VERSION = "1";

	private FactoryRoutingNetwork() {
	}

	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
		registrar.playToServer(RouteConfigurationPayload.TYPE, RouteConfigurationPayload.STREAM_CODEC,
			FactoryRoutingNetwork::handleConfiguration);
		registrar.playToServer(RequestPanelRoutingPayload.TYPE, RequestPanelRoutingPayload.STREAM_CODEC,
			FactoryRoutingNetwork::handleRequest);
		registrar.playToClient(SyncPanelRoutingPayload.TYPE, SyncPanelRoutingPayload.STREAM_CODEC,
			FactoryRoutingNetwork::handleSync);
		if (ModList.get().isLoaded("createfactorycontroller"))
			FactoryControllerNetwork.register(registrar);
	}

	/** Called only from client screens. */
	public static void request(FactoryPanelPosition panel) {
		PacketDistributor.sendToServer(new RequestPanelRoutingPayload(panel));
	}

	/** Called only from client screens. */
	public static void configure(FactoryPanelPosition panel, FactoryPanelPosition source,
		IngredientRoute route) {
		PacketDistributor.sendToServer(new RouteConfigurationPayload(panel, source, route));
	}

	/** Broadcasts the authoritative state after a server-side command or packet update. */
	public static void sync(FactoryPanelBlockEntity panel, FactoryPanelBehaviour behaviour) {
		if (!(panel.getLevel() instanceof ServerLevel level))
			return;
		SyncPanelRoutingPayload payload = new SyncPanelRoutingPayload(behaviour.getPanelPosition(),
			RoutingAccess.get(panel, behaviour.slot));
		PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(panel.getBlockPos()), payload);
	}

	private static void handleConfiguration(RouteConfigurationPayload payload, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player))
			return;
		PanelSelection selection = findPanel(player, payload.panel());
		if (selection == null || !selection.behaviour().targetedBy.containsKey(payload.source()))
			return;

		// The ingredient may be sourced from a different logistics network than the panel it feeds,
		// so being allowed to touch the target panel is not enough on its own.
		FactoryPanelBehaviour source = FactoryPanelBehaviour.at(player.serverLevel(), payload.source());
		if (source == null || !Create.LOGISTICS.mayInteract(source.network, player))
			return;

		IngredientRoute route = payload.route();
		PanelRouting current = RoutingAccess.get(selection.panel(), selection.behaviour().slot);
		RoutingAccess.set(selection.panel(), selection.behaviour().slot, current.with(payload.source(), route));
		selection.panel().notifyUpdate();
		sync(selection.panel(), selection.behaviour());
	}

	private static void handleRequest(RequestPanelRoutingPayload payload, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player))
			return;
		PanelSelection selection = findPanel(player, payload.panel());
		if (selection == null)
			return;
		context.reply(new SyncPanelRoutingPayload(payload.panel(),
			RoutingAccess.get(selection.panel(), selection.behaviour().slot)));
	}

	private static void handleSync(SyncPanelRoutingPayload payload, IPayloadContext context) {
		Level level = context.player().level();
		if (!level.isLoaded(payload.panel().pos()))
			return;
		BlockEntity blockEntity = level.getBlockEntity(payload.panel().pos());
		if (blockEntity instanceof FactoryPanelBlockEntity panel)
			RoutingAccess.set(panel, payload.panel().slot(), payload.routing());
	}

	private static PanelSelection findPanel(ServerPlayer player, FactoryPanelPosition position) {
		if (player.isSpectator() || AdventureUtil.isAdventure(player))
			return null;
		ServerLevel level = player.serverLevel();
		if (!level.isLoaded(position.pos()) || !player.canInteractWithBlock(position.pos(), 20))
			return null;
		BlockEntity blockEntity = level.getBlockEntity(position.pos());
		if (!(blockEntity instanceof FactoryPanelBlockEntity panel))
			return null;
		FactoryPanelBehaviour behaviour = panel.panels.get(position.slot());
		if (behaviour == null || !Create.LOGISTICS.mayInteract(behaviour.network, player))
			return null;
		return new PanelSelection(panel, behaviour);
	}

	private record PanelSelection(FactoryPanelBlockEntity panel, FactoryPanelBehaviour behaviour) {
	}

	private record RouteConfigurationPayload(FactoryPanelPosition panel, FactoryPanelPosition source,
		IngredientRoute route) implements CustomPacketPayload {

		private static final Type<RouteConfigurationPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(FactoryRouting.ID, "configure_route"));
		private static final StreamCodec<RegistryFriendlyByteBuf, RouteConfigurationPayload> STREAM_CODEC =
			StreamCodec.composite(
				FactoryPanelPosition.STREAM_CODEC, RouteConfigurationPayload::panel,
				FactoryPanelPosition.STREAM_CODEC, RouteConfigurationPayload::source,
				IngredientRoute.STREAM_CODEC, RouteConfigurationPayload::route,
				RouteConfigurationPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private record RequestPanelRoutingPayload(FactoryPanelPosition panel) implements CustomPacketPayload {

		private static final Type<RequestPanelRoutingPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(FactoryRouting.ID, "request_panel_routing"));
		private static final StreamCodec<RegistryFriendlyByteBuf, RequestPanelRoutingPayload> STREAM_CODEC =
			StreamCodec.composite(FactoryPanelPosition.STREAM_CODEC, RequestPanelRoutingPayload::panel,
				RequestPanelRoutingPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private record SyncPanelRoutingPayload(FactoryPanelPosition panel, PanelRouting routing)
		implements CustomPacketPayload {

		private static final Type<SyncPanelRoutingPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(FactoryRouting.ID, "sync_panel_routing"));
		private static final StreamCodec<RegistryFriendlyByteBuf, SyncPanelRoutingPayload> STREAM_CODEC =
			StreamCodec.composite(
				FactoryPanelPosition.STREAM_CODEC, SyncPanelRoutingPayload::panel,
				PanelRouting.STREAM_CODEC, SyncPanelRoutingPayload::routing,
				SyncPanelRoutingPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
