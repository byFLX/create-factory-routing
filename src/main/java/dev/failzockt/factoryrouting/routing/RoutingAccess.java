package dev.failzockt.factoryrouting.routing;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;

import dev.failzockt.factoryrouting.FactoryRouting;

/** Reads and writes the routing attachment on Create's factory panel block entities. */
public final class RoutingAccess {

	private RoutingAccess() {
	}

	public static BoardRouting board(FactoryPanelBlockEntity be) {
		return be.getData(FactoryRouting.BOARD_ROUTING);
	}

	public static PanelRouting get(FactoryPanelBlockEntity be, PanelSlot slot) {
		return board(be).get(slot);
	}

	public static PanelRouting get(FactoryPanelBehaviour behaviour) {
		return get(behaviour.panelBE(), behaviour.slot);
	}

	public static void set(FactoryPanelBlockEntity be, PanelSlot slot, PanelRouting routing) {
		BoardRouting current = board(be);
		BoardRouting updated = current.with(slot, routing);
		if (updated.equals(current))
			return;
		if (updated.isEmpty())
			be.removeData(FactoryRouting.BOARD_ROUTING);
		else
			be.setData(FactoryRouting.BOARD_ROUTING, updated);
		be.setChanged();
	}

	/**
	 * Drops overrides for ingredient links that no longer exist. Cheap enough to run on the panel
	 * tick because it only writes when something actually changed -- otherwise the block entity
	 * would be marked dirty every tick.
	 */
	public static void prune(FactoryPanelBehaviour behaviour) {
		FactoryPanelBlockEntity be = behaviour.panelBE();
		PanelRouting routing = get(be, behaviour.slot);
		if (routing.isEmpty())
			return;
		PanelRouting pruned = routing.prunedTo(behaviour.targetedBy.keySet());
		if (pruned != routing)
			set(be, behaviour.slot, pruned);
	}
}
