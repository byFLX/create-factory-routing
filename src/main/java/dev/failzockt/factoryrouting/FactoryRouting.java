package dev.failzockt.factoryrouting;

import java.util.function.Supplier;

import dev.failzockt.factoryrouting.client.EmbeddedRoutingInputEvents;
import dev.failzockt.factoryrouting.command.FactoryRoutingCommands;
import dev.failzockt.factoryrouting.network.FactoryRoutingNetwork;
import dev.failzockt.factoryrouting.routing.BoardRouting;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(FactoryRouting.ID)
public class FactoryRouting {

	public static final String ID = "create_factory_routing";

	private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
		DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ID);

	/**
	 * Per-block-entity routing overrides, attached to Create's {@code FactoryPanelBlockEntity}.
	 *
	 * <p>Block entity attachments are not synchronised to clients automatically on 1.21.1; the GUI
	 * gets its copy through a dedicated payload (SPEC.md §7).
	 */
	public static final Supplier<AttachmentType<BoardRouting>> BOARD_ROUTING =
		ATTACHMENT_TYPES.register("board_routing", () -> AttachmentType
			.builder(() -> BoardRouting.EMPTY)
			.serialize(BoardRouting.CODEC)
			.build());

	public FactoryRouting(IEventBus modBus) {
		ATTACHMENT_TYPES.register(modBus);
		modBus.addListener(FactoryRoutingNetwork::register);
		NeoForge.EVENT_BUS.addListener(FactoryRoutingCommands::register);
		if (FMLEnvironment.dist == Dist.CLIENT)
			EmbeddedRoutingInputEvents.register();
	}
}
