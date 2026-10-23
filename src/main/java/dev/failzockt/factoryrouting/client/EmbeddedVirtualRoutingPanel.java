package dev.failzockt.factoryrouting.client;

import java.util.List;

import dev.failzockt.factoryrouting.compat.factorycontroller.FactoryControllerNetwork;
import dev.failzockt.factoryrouting.compat.factorycontroller.VirtualPanelRouting;
import dev.failzockt.factoryrouting.compat.factorycontroller.VirtualRoutingAccess;
import dev.failzockt.factoryrouting.routing.IngredientRoute;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Embedded routing interface for a Factory Controller virtual recipe gauge. */
public final class EmbeddedVirtualRoutingPanel extends AbstractEmbeddedRoutingPanel<VirtualComponentPosition> {

	private final FactoryControllerMenu menu;
	private final BlockPos controllerPos;
	private final VirtualComponentPosition gaugePos;

	public EmbeddedVirtualRoutingPanel(FactoryControllerMenu menu, BlockPos controllerPos,
		VirtualComponentPosition gaugePos, List<VirtualComponentPosition> connections) {
		super(connections);
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
		return gauge != null && gauge.incomingConnection(connection, LogisticsConnection.TYPE)
			instanceof LogisticsConnection logistics
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
	protected void saveRoute(VirtualComponentPosition connection, IngredientRoute route) {
		VirtualGaugeBehaviour gauge = targetGauge();
		if (gauge == null)
			return;
		VirtualPanelRouting current = VirtualRoutingAccess.get(gauge);
		VirtualRoutingAccess.set(gauge, current.with(connection, route));
		FactoryControllerNetwork.configure(controllerPos, gaugePos, connection, route);
	}
}
