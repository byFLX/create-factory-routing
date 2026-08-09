package dev.failzockt.factoryrouting.compat.factorycontroller;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.AdventureUtil;

import dev.failzockt.factoryrouting.FactoryRouting;
import dev.failzockt.factoryrouting.routing.IngredientRoute;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Network bridge used only when Create: Factory Controller is installed. */
public final class FactoryControllerNetwork {

	private FactoryControllerNetwork() {
	}

	public static void register(PayloadRegistrar registrar) {
		registrar.playToServer(ConfigureVirtualRoutePayload.TYPE, ConfigureVirtualRoutePayload.STREAM_CODEC,
			FactoryControllerNetwork::handleConfiguration);
	}

	public static void configure(BlockPos controller, VirtualComponentPosition gauge,
		VirtualComponentPosition source, IngredientRoute route) {
		PacketDistributor.sendToServer(new ConfigureVirtualRoutePayload(controller, gauge, source, route));
	}

	private static void handleConfiguration(ConfigureVirtualRoutePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			VirtualSelection selection = findGauge(player, payload.controller(), payload.gauge(), payload.source());
			if (selection == null)
				return;

			VirtualPanelRouting current = VirtualRoutingAccess.get(selection.gauge());
			VirtualRoutingAccess.set(selection.gauge(), current.with(payload.source(), payload.route()));
			selection.controller().setChanged();
			selection.controller().syncComponentFull(payload.gauge());
		});
	}

	private static VirtualSelection findGauge(ServerPlayer player, BlockPos controllerPos,
		VirtualComponentPosition gaugePos, VirtualComponentPosition sourcePos) {
		if (player.isSpectator() || AdventureUtil.isAdventure(player))
			return null;
		ServerLevel level = player.serverLevel();
		if (!level.isLoaded(controllerPos) || !player.canInteractWithBlock(controllerPos, 20))
			return null;
		if (!(level.getBlockEntity(controllerPos) instanceof FactoryControllerBlockEntity controller))
			return null;
		if (!(controller.components.get(gaugePos) instanceof VirtualGaugeBehaviour gauge))
			return null;
		if (!(gauge.targetedBy().get(sourcePos) instanceof LogisticsConnection))
			return null;
		if (!(controller.components.get(sourcePos) instanceof VirtualGaugeBehaviour source))
			return null;
		if (!Create.LOGISTICS.mayInteract(gauge.networkId, player)
			|| !Create.LOGISTICS.mayInteract(source.networkId, player))
			return null;
		return new VirtualSelection(controller, gauge);
	}

	private record VirtualSelection(FactoryControllerBlockEntity controller, VirtualGaugeBehaviour gauge) {
	}

	private record ConfigureVirtualRoutePayload(BlockPos controller, VirtualComponentPosition gauge,
		VirtualComponentPosition source, IngredientRoute route) implements CustomPacketPayload {

		private static final Type<ConfigureVirtualRoutePayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(FactoryRouting.ID, "configure_virtual_route"));

		private static final StreamCodec<RegistryFriendlyByteBuf, ConfigureVirtualRoutePayload> STREAM_CODEC =
			StreamCodec.of(
				(buffer, payload) -> {
					buffer.writeBlockPos(payload.controller());
					buffer.writeInt(payload.gauge().x());
					buffer.writeInt(payload.gauge().y());
					buffer.writeInt(payload.source().x());
					buffer.writeInt(payload.source().y());
					IngredientRoute.STREAM_CODEC.encode(buffer, payload.route());
				},
				buffer -> new ConfigureVirtualRoutePayload(
					buffer.readBlockPos(),
					new VirtualComponentPosition(buffer.readInt(), buffer.readInt()),
					new VirtualComponentPosition(buffer.readInt(), buffer.readInt()),
					IngredientRoute.STREAM_CODEC.decode(buffer)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
