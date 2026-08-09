package dev.failzockt.factoryrouting.client;

import java.util.List;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;

import dev.failzockt.factoryrouting.network.FactoryRoutingNetwork;
import dev.failzockt.factoryrouting.routing.IngredientRoute;
import dev.failzockt.factoryrouting.routing.RoutingAccess;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/** Compact ingredient routing list for a physical Create factory panel. */
public class RoutingOverviewScreen extends AbstractRoutingOverviewScreen<FactoryPanelConnection> {

	private final FactoryPanelBehaviour behaviour;

	public RoutingOverviewScreen(Screen parent, FactoryPanelBehaviour behaviour,
		List<FactoryPanelConnection> connections) {
		super(parent, connections);
		this.behaviour = behaviour;
	}

	@Override
	protected ItemStack ingredient(FactoryPanelConnection connection) {
		FactoryPanelBehaviour source = FactoryPanelBehaviour.at(minecraft.level, connection.from);
		return source == null ? ItemStack.EMPTY : source.getFilter();
	}

	@Override
	protected int amount(FactoryPanelConnection connection) {
		return connection.amount;
	}

	@Override
	protected IngredientRoute route(FactoryPanelConnection connection) {
		return RoutingAccess.get(behaviour).get(connection.from);
	}

	@Override
	protected String inheritedAddress() {
		return behaviour.recipeAddress;
	}

	@Override
	protected Screen editorFor(FactoryPanelConnection connection) {
		return new IngredientRouteScreen(this, behaviour, connection.from);
	}

	@Override
	protected void requestSync() {
		FactoryRoutingNetwork.request(behaviour.getPanelPosition());
	}
}
