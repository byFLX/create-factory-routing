package dev.failzockt.factoryrouting.client;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import dev.failzockt.factoryrouting.network.FactoryRoutingNetwork;
import dev.failzockt.factoryrouting.routing.IngredientRoute;
import dev.failzockt.factoryrouting.routing.PanelRouting;
import dev.failzockt.factoryrouting.routing.RoutingAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/** Address editor opened from one ingredient of a physical Create factory panel. */
public class IngredientRouteScreen extends AbstractIngredientRouteScreen {

	private final FactoryPanelBehaviour behaviour;
	private final FactoryPanelPosition source;
	private final ItemStack item;
	private final int ingredientAmount;

	public IngredientRouteScreen(Screen parent, FactoryPanelBehaviour behaviour,
		FactoryPanelPosition source) {
		super(parent, RoutingAccess.get(behaviour).get(source).address());
		this.behaviour = behaviour;
		this.source = source;

		FactoryPanelConnection connection = behaviour.targetedBy.get(source);
		ingredientAmount = connection == null ? 0 : connection.amount;
		FactoryPanelBehaviour sourceBehaviour = FactoryPanelBehaviour.at(Minecraft.getInstance().level, source);
		item = sourceBehaviour == null ? ItemStack.EMPTY : sourceBehaviour.getFilter().copy();
	}

	@Override
	protected ItemStack ingredient() {
		return item;
	}

	@Override
	protected int amount() {
		return ingredientAmount;
	}

	@Override
	protected String inheritedAddress() {
		return behaviour.recipeAddress;
	}

	@Override
	protected IngredientRoute currentRoute() {
		return RoutingAccess.get(behaviour).get(source);
	}

	@Override
	protected void saveRoute(IngredientRoute route) {
		PanelRouting current = RoutingAccess.get(behaviour);
		RoutingAccess.set(behaviour.panelBE(), behaviour.slot, current.with(source, route));
		FactoryRoutingNetwork.configure(behaviour.getPanelPosition(), source, route);
	}

	@Override
	protected void requestSync() {
		FactoryRoutingNetwork.request(behaviour.getPanelPosition());
	}
}
