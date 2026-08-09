package dev.failzockt.factoryrouting.command;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import dev.failzockt.factoryrouting.network.FactoryRoutingNetwork;
import dev.failzockt.factoryrouting.routing.IngredientRoute;
import dev.failzockt.factoryrouting.routing.PanelRouting;
import dev.failzockt.factoryrouting.routing.RoutingAccess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator-only commands for exercising routed dispatch before the configuration UI exists.
 *
 * <p>Both panel positions are interpreted in the command source's current dimension. Slots use
 * their serialized names: {@code top_left}, {@code top_right}, {@code bottom_left} and
 * {@code bottom_right}.
 */
public final class FactoryRoutingCommands {

	private static final String SLOT_NAMES = Arrays.stream(PanelSlot.values())
		.map(PanelSlot::getSerializedName)
		.collect(Collectors.joining(", "));

	private static final DynamicCommandExceptionType INVALID_SLOT = new DynamicCommandExceptionType(value ->
		Component.literal("Unknown panel slot '" + value + "'. Expected one of: " + SLOT_NAMES));
	private static final DynamicCommandExceptionType NOT_A_PANEL = new DynamicCommandExceptionType(value ->
		Component.literal("No factory panel block entity at " + value));
	private static final DynamicCommandExceptionType MISSING_PANEL_SLOT = new DynamicCommandExceptionType(value ->
		Component.literal("The factory panel has no active slot " + value));
	private static final DynamicCommandExceptionType UNKNOWN_INGREDIENT = new DynamicCommandExceptionType(value ->
		Component.literal("The selected panel slot has no ingredient link from " + value));
	private static final DynamicCommandExceptionType INVALID_ADDRESS = new DynamicCommandExceptionType(value ->
		Component.literal("Invalid target address: " + value));

	private FactoryRoutingCommands() {
	}

	public static void register(RegisterCommandsEvent event) {
		CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
		dispatcher.register(root("create_factory_routing"));
		dispatcher.register(root("cfr"));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
		return Commands.literal(name)
			.requires(source -> source.hasPermission(2))
			.then(Commands.literal("route")
				.then(Commands.literal("set")
					.then(targetPanelArguments(true)))
				.then(Commands.literal("clear")
					.then(targetPanelArguments(false)))
				.then(Commands.literal("list")
					.then(Commands.argument("panel", BlockPosArgument.blockPos())
						.then(slotArgument("slot")
							.executes(FactoryRoutingCommands::list)))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?>
		targetPanelArguments(boolean set) {
		com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> sourceSlot =
			slotArgument("source_slot");
		if (set)
			sourceSlot.then(Commands.argument("address", StringArgumentType.greedyString())
				.executes(FactoryRoutingCommands::set));
		else
			sourceSlot.executes(FactoryRoutingCommands::clear);

		return Commands.argument("panel", BlockPosArgument.blockPos())
			.then(slotArgument("slot")
				.then(Commands.argument("source", BlockPosArgument.blockPos())
					.then(sourceSlot)));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
		slotArgument(String name) {
		return Commands.argument(name, StringArgumentType.word())
			.suggests((context, builder) -> {
				for (PanelSlot slot : PanelSlot.values())
					builder.suggest(slot.getSerializedName());
				return builder.buildFuture();
			});
	}

	private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Selection selection = selection(context);
		String address = StringArgumentType.getString(context, "address").trim();
		if (address.isBlank())
			throw INVALID_ADDRESS.create("the address cannot be blank; use 'route clear' to inherit");
		if (address.length() > IngredientRoute.MAX_ADDRESS_LENGTH)
			throw INVALID_ADDRESS.create("maximum length is " + IngredientRoute.MAX_ADDRESS_LENGTH
				+ " characters, got " + address.length());

		PanelRouting current = RoutingAccess.get(selection.panel(), selection.slot());
		RoutingAccess.set(selection.panel(), selection.slot(),
			current.with(selection.source(), new IngredientRoute(address)));
		FactoryRoutingNetwork.sync(selection.panel(), selection.behaviour());

		context.getSource()
			.sendSuccess(() -> Component.literal("Route set: " + describe(selection.source()) + " -> " + address), true);
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Selection selection = selection(context);
		PanelRouting current = RoutingAccess.get(selection.panel(), selection.slot());
		RoutingAccess.set(selection.panel(), selection.slot(),
			current.with(selection.source(), IngredientRoute.NONE));
		FactoryRoutingNetwork.sync(selection.panel(), selection.behaviour());

		String inherited = selection.behaviour().recipeAddress;
		context.getSource()
			.sendSuccess(() -> Component.literal("Route cleared: " + describe(selection.source())
				+ " now inherits '" + inherited + "'"), true);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		FactoryPanelBlockEntity panel = panel(context);
		PanelSlot slot = slot(context, "slot");
		FactoryPanelBehaviour behaviour = behaviour(panel, slot);
		PanelRouting routing = RoutingAccess.get(panel, slot);

		context.getSource()
			.sendSuccess(() -> Component.literal("Ingredient routes for "
				+ panel.getBlockPos().toShortString() + " " + slot.getSerializedName() + ":"), false);
		if (behaviour.targetedBy.isEmpty()) {
			context.getSource()
				.sendSuccess(() -> Component.literal("  no ingredient links"), false);
			return 1;
		}

		ServerLevel level = context.getSource().getLevel();
		for (FactoryPanelConnection connection : behaviour.targetedBy.values()) {
			FactoryPanelPosition source = connection.from;
			IngredientRoute route = routing.get(source);
			String effective = routing.resolve(source, behaviour.recipeAddress);
			FactoryPanelBehaviour sourceBehaviour = FactoryPanelBehaviour.at(level, connection);
			String item = sourceBehaviour == null || sourceBehaviour.getFilter().isEmpty()
				? "unknown item"
				: sourceBehaviour.getFilter().getHoverName().getString();
			String state = route.isOverride() ? "override" : "inherited";
			context.getSource()
				.sendSuccess(() -> Component.literal("  " + describe(source) + " | " + item + " x"
					+ connection.amount + " -> '" + effective + "' (" + state + ")"), false);
		}
		return 1;
	}

	private static Selection selection(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		FactoryPanelBlockEntity panel = panel(context);
		PanelSlot slot = slot(context, "slot");
		FactoryPanelBehaviour behaviour = behaviour(panel, slot);
		BlockPos sourcePos = BlockPosArgument.getBlockPos(context, "source");
		PanelSlot sourceSlot = slot(context, "source_slot");
		FactoryPanelPosition source = new FactoryPanelPosition(sourcePos, sourceSlot);
		if (!behaviour.targetedBy.containsKey(source))
			throw UNKNOWN_INGREDIENT.create(describe(source));
		return new Selection(panel, behaviour, slot, source);
	}

	private static FactoryPanelBlockEntity panel(CommandContext<CommandSourceStack> context)
		throws CommandSyntaxException {
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "panel");
		BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);
		if (blockEntity instanceof FactoryPanelBlockEntity panel)
			return panel;
		throw NOT_A_PANEL.create(pos.toShortString());
	}

	private static FactoryPanelBehaviour behaviour(FactoryPanelBlockEntity panel, PanelSlot slot)
		throws CommandSyntaxException {
		FactoryPanelBehaviour behaviour = panel.panels.get(slot);
		if (behaviour != null)
			return behaviour;
		throw MISSING_PANEL_SLOT.create(slot.getSerializedName());
	}

	private static PanelSlot slot(CommandContext<CommandSourceStack> context, String argument)
		throws CommandSyntaxException {
		String value = StringArgumentType.getString(context, argument).toLowerCase(Locale.ROOT);
		for (PanelSlot slot : PanelSlot.values())
			if (slot.getSerializedName().equals(value) || slot.name().toLowerCase(Locale.ROOT).equals(value))
				return slot;
		throw INVALID_SLOT.create(value);
	}

	private static String describe(FactoryPanelPosition position) {
		return position.pos().toShortString() + " " + position.slot().getSerializedName();
	}

	private record Selection(FactoryPanelBlockEntity panel, FactoryPanelBehaviour behaviour, PanelSlot slot,
		FactoryPanelPosition source) {
	}
}
