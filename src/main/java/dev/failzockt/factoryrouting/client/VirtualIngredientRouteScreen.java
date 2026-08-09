package dev.failzockt.factoryrouting.client;

import dev.failzockt.factoryrouting.compat.factorycontroller.FactoryControllerNetwork;
import dev.failzockt.factoryrouting.compat.factorycontroller.VirtualPanelRouting;
import dev.failzockt.factoryrouting.compat.factorycontroller.VirtualRoutingAccess;
import dev.failzockt.factoryrouting.routing.IngredientRoute;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Address editor for one ingredient wire of a Factory Controller virtual gauge. */
public class VirtualIngredientRouteScreen extends AbstractIngredientRouteScreen {

	private final FactoryControllerMenu menu;
	private final BlockPos controllerPos;
	private final VirtualComponentPosition gaugePos;
	private final VirtualComponentPosition sourcePos;

	public VirtualIngredientRouteScreen(Screen parent, FactoryControllerMenu menu, BlockPos controllerPos,
		VirtualComponentPosition gaugePos, VirtualComponentPosition sourcePos) {
		super(parent, initialRoute(menu, gaugePos, sourcePos).address());
		this.menu = menu;
		this.controllerPos = controllerPos;
		this.gaugePos = gaugePos;
		this.sourcePos = sourcePos;
	}

	private static IngredientRoute initialRoute(FactoryControllerMenu menu,
		VirtualComponentPosition gaugePos, VirtualComponentPosition sourcePos) {
		return menu.componentAt(gaugePos) instanceof VirtualGaugeBehaviour gauge
			? VirtualRoutingAccess.get(gauge).get(sourcePos) : IngredientRoute.NONE;
	}

	private VirtualGaugeBehaviour targetGauge() {
		return menu.componentAt(gaugePos) instanceof VirtualGaugeBehaviour gauge ? gauge : null;
	}

	private VirtualGaugeBehaviour sourceGauge() {
		return menu.componentAt(sourcePos) instanceof VirtualGaugeBehaviour gauge ? gauge : null;
	}

	@Override
	protected ItemStack ingredient() {
		VirtualGaugeBehaviour source = sourceGauge();
		return source == null ? ItemStack.EMPTY : source.filter;
	}

	@Override
	protected int amount() {
		VirtualGaugeBehaviour gauge = targetGauge();
		return gauge != null && gauge.targetedBy().get(sourcePos) instanceof LogisticsConnection connection
			? connection.amount() : 0;
	}

	@Override
	protected String inheritedAddress() {
		VirtualGaugeBehaviour gauge = targetGauge();
		return gauge == null ? "" : gauge.recipeAddress;
	}

	@Override
	protected IngredientRoute currentRoute() {
		VirtualGaugeBehaviour gauge = targetGauge();
		return gauge == null ? IngredientRoute.NONE : VirtualRoutingAccess.get(gauge).get(sourcePos);
	}

	@Override
	protected void saveRoute(IngredientRoute route) {
		VirtualGaugeBehaviour gauge = targetGauge();
		if (gauge == null)
			return;
		VirtualPanelRouting current = VirtualRoutingAccess.get(gauge);
		VirtualRoutingAccess.set(gauge, current.with(sourcePos, route));
		FactoryControllerNetwork.configure(controllerPos, gaugePos, sourcePos, route);
	}
}
