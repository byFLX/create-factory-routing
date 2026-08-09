package dev.failzockt.factoryrouting.client;

import java.util.List;

import dev.failzockt.factoryrouting.compat.factorycontroller.VirtualRoutingAccess;
import dev.failzockt.factoryrouting.routing.IngredientRoute;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Compact ingredient routing list for a Factory Controller virtual gauge. */
public class VirtualRoutingOverviewScreen extends AbstractRoutingOverviewScreen<VirtualComponentPosition> {

	private final FactoryControllerMenu menu;
	private final BlockPos controllerPos;
	private final VirtualComponentPosition gaugePos;

	public VirtualRoutingOverviewScreen(Screen parent, FactoryControllerMenu menu, BlockPos controllerPos,
		VirtualComponentPosition gaugePos, List<VirtualComponentPosition> connections) {
		super(parent, connections);
		this.menu = menu;
		this.controllerPos = controllerPos;
		this.gaugePos = gaugePos;
	}

	private VirtualGaugeBehaviour targetGauge() {
		return menu.componentAt(gaugePos) instanceof VirtualGaugeBehaviour gauge ? gauge : null;
	}

	@Override
	protected ItemStack ingredient(VirtualComponentPosition connection) {
		return menu.componentAt(connection) instanceof VirtualGaugeBehaviour gauge
			? gauge.filter : ItemStack.EMPTY;
	}

	@Override
	protected int amount(VirtualComponentPosition connection) {
		VirtualGaugeBehaviour gauge = targetGauge();
		return gauge != null && gauge.targetedBy().get(connection) instanceof LogisticsConnection logistics
			? logistics.amount() : 0;
	}

	@Override
	protected IngredientRoute route(VirtualComponentPosition connection) {
		VirtualGaugeBehaviour gauge = targetGauge();
		return gauge == null ? IngredientRoute.NONE : VirtualRoutingAccess.get(gauge).get(connection);
	}

	@Override
	protected String inheritedAddress() {
		VirtualGaugeBehaviour gauge = targetGauge();
		return gauge == null ? "" : gauge.recipeAddress;
	}

	@Override
	protected Screen editorFor(VirtualComponentPosition connection) {
		return new VirtualIngredientRouteScreen(this, menu, controllerPos, gaugePos, connection);
	}
}
