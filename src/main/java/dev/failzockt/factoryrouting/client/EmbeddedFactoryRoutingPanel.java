package dev.failzockt.factoryrouting.client;

import java.util.List;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;

import dev.failzockt.factoryrouting.network.FactoryRoutingNetwork;
import dev.failzockt.factoryrouting.routing.IngredientRoute;
import dev.failzockt.factoryrouting.routing.PanelRouting;
import dev.failzockt.factoryrouting.routing.RoutingAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Embedded routing interface for a physical Create Factory Panel. */
public final class EmbeddedFactoryRoutingPanel extends AbstractEmbeddedRoutingPanel<FactoryPanelConnection> {

	private final FactoryPanelBehaviour behaviour;

	public EmbeddedFactoryRoutingPanel(FactoryPanelBehaviour behaviour,
		List<FactoryPanelConnection> connections) {
		super(connections);
		this.behaviour = behaviour;
	}

	@Override
	protected ItemStack ingredient(FactoryPanelConnection connection) {
		FactoryPanelBehaviour source = FactoryPanelBehaviour.at(Minecraft.getInstance().level, connection.from);
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
	protected void saveRoute(FactoryPanelConnection connection, IngredientRoute route) {
		PanelRouting current = RoutingAccess.get(behaviour);
		RoutingAccess.set(behaviour.panelBE(), behaviour.slot, current.with(connection.from, route));
		FactoryRoutingNetwork.configure(behaviour.getPanelPosition(), connection.from, route);
	}

	@Override
	protected void requestSync() {
		FactoryRoutingNetwork.request(behaviour.getPanelPosition());
	}
}
